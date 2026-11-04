package se.comerit.seb.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.ChangeUserRoleRequest;
import se.comerit.seb.dto.CreateUserRequest;
import se.comerit.seb.dto.UserResponse;
import se.comerit.seb.exception.EmailAlreadyInUseException;
import se.comerit.seb.exception.RoleChangeConflictException;
import se.comerit.seb.exception.UserAdminRefusedException;
import se.comerit.seb.exception.UserDeleteConflictException;
import se.comerit.seb.exception.UserNotFoundException;
import se.comerit.seb.repository.ApprovalStepRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.RefreshTokenRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Användaradministration för ADMIN (ADR 0017). Admin ser och skapar användare i det egna företaget.
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

    static final String DELETE_BLOCKED_BY_HISTORY =
            "Användaren har betalningar eller atteststeg kopplade till sig och kan inte tas bort, "
                    + "eftersom historiken måste bevaras.";

    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final PaymentRepository paymentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAdminService(UserRepository userRepository,
                            ApprovalStepRepository approvalStepRepository,
                            PaymentRepository paymentRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService) {
        this.userRepository = userRepository;
        this.approvalStepRepository = approvalStepRepository;
        this.paymentRepository = paymentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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

    // Byter roll mellan INITIATOR och ATTESTANT för en användare i admins företag.
    //  - Användarens rad låses medan det pågår, och ett annat företags användare ser ut som en som inte finns (404).
    //  - En administratörs roll ändras aldrig här (403). Det gäller också admins egen, så ingen kan ge sig själv
    //    eller någon annan mer makt, eller ta bort den sista administratören.
    //  - En attestant med väntande steg kan inte bli något annat (409): stegen skulle ligga kvar hos någon som
    //    inte längre kan fatta beslut.
    //  - Samma roll som förut är ett lyckat anrop utan ändring och utan audit-rad.
    // Rollen ligger i användarens token, så ändringen slår igenom när inloggningen förnyas, högst 15 minuter
    // senare (ADR 0012). Betalningsflödet tål mellantiden: beslut kontrolleras mot steget och skaparen, och en
    // betalning tilldelas aldrig sin skapare.
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse changeRole(AuthenticatedUserContext admin, Long userId, ChangeUserRoleRequest request) {
        Long tenantId = requireTenant(admin);
        Role newRole = validRole(request == null ? null : request.role());

        User user = userRepository.findByIdAndTenantIdForUpdate(userId, tenantId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getRole() == Role.ADMIN) {
            throw new UserAdminRefusedException("Rollen för en administratör kan inte ändras här.");
        }

        Role oldRole = user.getRole();
        if (oldRole == newRole) {
            return UserResponse.from(user);
        }

        if (oldRole == Role.ATTESTANT
                && approvalStepRepository.existsByAttestantIdAndStatus(user.getId(), ApprovalStepStatus.PENDING)) {
            throw new RoleChangeConflictException(
                    "Attestanten har väntande atteststeg. Låt dem godkännas eller avvisas innan rollen ändras.");
        }

        user.changeRole(newRole);

        auditService.record(
                tenantId,
                admin.userId(),
                "UPDATE_USER_ROLE",
                "USER",
                user.getId(),
                "Roll ändrad för %s (%s): %s till %s".formatted(
                        user.getName(),
                        user.getEmail(),
                        oldRole == null ? "ingen" : oldRole.name().toLowerCase(Locale.ROOT),
                        newRole.name().toLowerCase(Locale.ROOT)));

        return UserResponse.from(user);
    }

    // Tar bort en användare permanent, i admins företag.
    //  - Användarens rad låses medan det pågår, och ett annat företags användare ser ut som en som inte finns (404).
    //  - En administratör tas aldrig bort här (403), inte heller admin själv: ingen ska kunna ta bort den sista
    //    administratören eller låsa ut sig.
    //  - En användare som skapat betalningar, fått atteststeg tilldelade eller fattat beslut kan inte tas bort (409).
    //    Betalningarna och stegen pekar på användaren, och historiken (vem som skapade och godkände vad) måste
    //    bevaras. Därför går det bara att ta bort en användare som aldrig gjort något, till exempel ett konto som
    //    skapats av misstag.
    //  - Användarens refresh-token tas bort först, så att hen inte kan förnya sin inloggning. Ett redan utfärdat
    //    access-token gäller ändå tills det går ut, högst 15 minuter (ADR 0012).
    //  - Borttagningen loggas i audit med namn och e-post, eftersom raden i users är borta efteråt.
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteUser(AuthenticatedUserContext admin, Long userId) {
        Long tenantId = requireTenant(admin);

        User user = userRepository.findByIdAndTenantIdForUpdate(userId, tenantId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getRole() == Role.ADMIN) {
            throw new UserAdminRefusedException("En administratör kan inte tas bort.");
        }

        if (paymentRepository.existsByCreatedBy(user.getId())
                || approvalStepRepository.existsByAttestantId(user.getId())
                || approvalStepRepository.existsByDecidedBy(user.getId())) {
            throw new UserDeleteConflictException(DELETE_BLOCKED_BY_HISTORY);
        }

        refreshTokenRepository.deleteAllForUser(user.getId());

        try {
            // flush, så att databasens främmande nycklar slår till här. En betalning eller ett steg som hann
            // skapas för användaren mellan kontrollen ovan och borttagningen fångas då som 409.
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException referenced) {
            throw new UserDeleteConflictException(DELETE_BLOCKED_BY_HISTORY);
        }

        auditService.record(
                tenantId,
                admin.userId(),
                "DELETE_USER",
                "USER",
                user.getId(),
                "Användare borttagen: %s (%s), roll %s".formatted(
                        user.getName(),
                        user.getEmail(),
                        user.getRole() == null ? "ingen" : user.getRole().name().toLowerCase(Locale.ROOT)));
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
            throw new IllegalArgumentException("Rollen " + role.name() + " kan inte tilldelas här. Välj INITIATOR eller ATTESTANT.");
        }
        return role;
    }
}
