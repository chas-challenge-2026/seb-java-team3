package se.comerit.seb.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.CreateUserRequest;
import se.comerit.seb.dto.UserResponse;
import se.comerit.seb.exception.EmailAlreadyInUseException;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Användaradministration för ADMIN (ADR 0015). Admin ser och skapar användare i det egna företaget.
// Företaget kommer alltid från admins token, aldrig från anropet.
@Service
public class UserAdminService {

    static final int NAME_MAX_LENGTH = 100;
    static final int EMAIL_MAX_LENGTH = 100;
    static final int PASSWORD_MIN_LENGTH = 12;
    // BCrypt använder bara de första 72 byten av lösenordet. Ett längre lösenord skulle sparas, men allt efter
    // byte 72 skulle ignoreras vid inloggning, så det avvisas hellre.
    static final int PASSWORD_MAX_BYTES = 72;

    // Bara dessa roller får skapas här. ADMIN skapas inte via API:t, så att ingen kan ge sig själv eller någon
    // annan mer makt genom ett anrop.
    private static final Set<Role> ASSIGNABLE_ROLES = EnumSet.of(Role.INITIATOR, Role.ATTESTANT);

    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAdminService(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // Aldrig hela användarobjektet: UserResponse har inget fält för lösenordshashen.
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(AuthenticatedUserContext admin) {
        return userRepository.findByTenantIdOrderByNameAscIdAsc(requireTenant(admin)).stream()
                .map(UserResponse::from)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse createUser(AuthenticatedUserContext admin, CreateUserRequest request) {
        Long tenantId = requireTenant(admin);

        String name = validName(request.name());
        String email = validEmail(request.email());
        String password = validPassword(request.password());
        Role role = validRole(request.role());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }

        User saved;
        try {
            // saveAndFlush, så att databasens unikhetskrav på e-post slår till här och inte först vid commit.
            // Två admins som skapar samma adress samtidigt passerar båda existsByEmail, och här fångas den
            // som kommer sist. Längd och format är kontrollerade ovan, så e-posten är den enda rimliga orsaken.
            saved = userRepository.saveAndFlush(
                    new User(tenantId, name, email, passwordEncoder.encode(password), role));
        } catch (DataIntegrityViolationException duplicate) {
            throw new EmailAlreadyInUseException();
        }

        // Aldrig lösenordet, varken i klartext eller som hash, i loggen.
        auditService.record(
                tenantId,
                admin.userId(),
                "CREATE_USER",
                "USER",
                saved.getId(),
                "Användare skapad: %s (%s), roll %s".formatted(name, email, role.name().toLowerCase(Locale.ROOT)));

        return UserResponse.from(saved);
    }

    // En användare utan företag får inte administrera något. tenantId kommer från token och ska alltid
    // finnas, men kontrollen får inte vila på det: ett null skulle annars bli "tenant_id IS NULL" i frågan.
    private static Long requireTenant(AuthenticatedUserContext admin) {
        if (admin == null || admin.tenantId() == null) {
            throw new AccessDeniedException("Användaren saknar företag");
        }
        return admin.tenantId();
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Namn måste anges.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Namnet får vara högst " + NAME_MAX_LENGTH + " tecken.");
        }
        return name;
    }

    private static String validEmail(String raw) {
        String email = EmailAddress.normalize(raw);
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("E-postadress måste anges.");
        }
        if (email.length() > EMAIL_MAX_LENGTH) {
            throw new IllegalArgumentException("E-postadressen får vara högst " + EMAIL_MAX_LENGTH + " tecken.");
        }
        if (!EMAIL_FORMAT.matcher(email).matches()) {
            throw new IllegalArgumentException("Ange en giltig e-postadress.");
        }
        return email;
    }

    // Lösenordet trimmas aldrig: mellanslag är en del av det.
    private static String validPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Lösenord måste anges.");
        }
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("Lösenordet måste vara minst " + PASSWORD_MIN_LENGTH + " tecken.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new IllegalArgumentException("Lösenordet är för långt (högst " + PASSWORD_MAX_BYTES + " byte).");
        }
        return password;
    }

    private static Role validRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Roll måste anges.");
        }

        Role role;
        try {
            role = Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Okänd roll. Välj INITIATOR eller ATTESTANT.");
        }

        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Rollen " + role.name() + " kan inte skapas här. Välj INITIATOR eller ATTESTANT.");
        }
        return role;
    }
}
