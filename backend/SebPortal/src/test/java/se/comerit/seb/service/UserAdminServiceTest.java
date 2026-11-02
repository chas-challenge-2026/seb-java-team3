package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.config.PasswordEncoderConfig;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.CreateUserRequest;
import se.comerit.seb.dto.UserResponse;
import se.comerit.seb.exception.EmailAlreadyInUseException;
import se.comerit.seb.repository.ApprovalStepRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Användaradministrationen (#242, ADR 0015): validering, företag från token, hashat lösenord, 409 på upptagen
// e-post och audit utan lösenord. Rollspärren (403 för andra än ADMIN) prövas på HTTP-nivå i controllertestet.
class UserAdminServiceTest {

    private static final Long TENANT = 7L;
    private static final String PASSWORD = "ett-langt-losenord";

    private final AuthenticatedUserContext admin = new AuthenticatedUserContext(99L, TENANT, Role.ADMIN);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();
    private final ApprovalStepRepository approvalStepRepository = mock(ApprovalStepRepository.class);
    private final UserAdminService service =
            new UserAdminService(userRepository, approvalStepRepository, passwordEncoder, auditService);

    @BeforeEach
    void savedUsersGetAnId() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 55L);
            return user;
        });
    }

    private static CreateUserRequest request(String name, String email, String password, String role) {
        return new CreateUserRequest(name, email, password, role);
    }

    // --- Skapa ---------------------------------------------------------------------------------------

    @Test
    void createUser_savesInTheAdminsCompany_withAHashedPassword() {
        UserResponse response = service.createUser(admin,
                request("  Nils Ek ", "  Nils.Ek@MalmoBygg.SE ", PASSWORD, "ATTESTANT"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();

        assertEquals(TENANT, user.getTenantId());
        assertEquals("Nils Ek", user.getName());
        assertEquals("nils.ek@malmobygg.se", user.getEmail());
        assertEquals(Role.ATTESTANT, user.getRole());
        // Lösenordet sparas aldrig i klartext, och hashen fungerar vid inloggning
        assertNotEquals(PASSWORD, user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$12$"));
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));

        assertEquals(new UserResponse(55L, "Nils Ek", "nils.ek@malmobygg.se", Role.ATTESTANT), response);
    }

    @Test
    void createUser_acceptsEitherAssignableRole_inAnyCasing() {
        assertEquals(Role.INITIATOR,
                service.createUser(admin, request("A", "a@x.se", PASSWORD, "initiator")).role());
        assertEquals(Role.ATTESTANT,
                service.createUser(admin, request("B", "b@x.se", PASSWORD, " Attestant ")).role());
    }

    @Test
    void createUser_writesAnAuditRowWithoutThePassword() {
        service.createUser(admin, request("Nils Ek", "nils@malmobygg.se", PASSWORD, "ATTESTANT"));

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq(TENANT), eq(99L), eq("CREATE_USER"), eq("USER"), eq(55L), description.capture());

        assertEquals("Användare skapad: Nils Ek (nils@malmobygg.se), roll attestant", description.getValue());
        assertFalse(description.getValue().contains(PASSWORD));
        assertFalse(description.getValue().contains("$2a$"));
    }

    @Test
    void createUser_acceptsTheBoundaryValues() {
        String longestName = "n".repeat(UserAdminService.NAME_MAX_LENGTH);
        String longestEmail = "a".repeat(UserAdminService.EMAIL_MAX_LENGTH - "@b.se".length()) + "@b.se";
        String shortestPassword = "x".repeat(UserAdminService.PASSWORD_MIN_LENGTH);
        String longestPassword = "x".repeat(UserAdminService.PASSWORD_MAX_BYTES);

        service.createUser(admin, request(longestName, longestEmail, shortestPassword, "INITIATOR"));
        service.createUser(admin, request("Lång", "lang@x.se", longestPassword, "INITIATOR"));
    }

    static Stream<Arguments> invalidInput() {
        String valid = "ett-langt-losenord";
        return Stream.of(
                Arguments.of(null, "a@x.se", valid, "ATTESTANT", "Namn måste anges."),
                Arguments.of("   ", "a@x.se", valid, "ATTESTANT", "Namn måste anges."),
                Arguments.of("n".repeat(101), "a@x.se", valid, "ATTESTANT", "Namnet får vara högst 100 tecken."),

                Arguments.of("A", null, valid, "ATTESTANT", "E-postadress måste anges."),
                Arguments.of("A", "  ", valid, "ATTESTANT", "E-postadress måste anges."),
                Arguments.of("A", "inte-en-adress", valid, "ATTESTANT", "Ange en giltig e-postadress."),
                Arguments.of("A", "a b@x.se", valid, "ATTESTANT", "Ange en giltig e-postadress."),
                Arguments.of("A", "a@@x.se", valid, "ATTESTANT", "Ange en giltig e-postadress."),
                Arguments.of("A", "a@x", valid, "ATTESTANT", "Ange en giltig e-postadress."),
                Arguments.of("A", "a".repeat(96) + "@b.se", valid, "ATTESTANT",
                        "E-postadressen får vara högst 100 tecken."),

                Arguments.of("A", "a@x.se", null, "ATTESTANT", "Lösenord måste anges."),
                Arguments.of("A", "a@x.se", "", "ATTESTANT", "Lösenord måste anges."),
                Arguments.of("A", "a@x.se", "elva-tecken", "ATTESTANT", "Lösenordet måste vara minst 12 tecken."),
                Arguments.of("A", "a@x.se", "x".repeat(73), "ATTESTANT", "Lösenordet är för långt (högst 72 byte)."),
                // 37 tecken men 74 byte: längden räknas i byte, eftersom BCrypt gör det
                Arguments.of("A", "a@x.se", "ö".repeat(37), "ATTESTANT", "Lösenordet är för långt (högst 72 byte)."),

                Arguments.of("A", "a@x.se", valid, null, "Roll måste anges."),
                Arguments.of("A", "a@x.se", valid, " ", "Roll måste anges."),
                Arguments.of("A", "a@x.se", valid, "CHEF", "Okänd roll. Välj INITIATOR eller ATTESTANT."),
                Arguments.of("A", "a@x.se", valid, "ADMIN",
                        "Rollen ADMIN kan inte skapas här. Välj INITIATOR eller ATTESTANT."),
                Arguments.of("A", "a@x.se", valid, "admin",
                        "Rollen ADMIN kan inte skapas här. Välj INITIATOR eller ATTESTANT.")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidInput")
    void createUser_refusesInvalidInput_andChangesNothing(
            String name, String email, String password, String role, String expectedMessage) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> service.createUser(admin, request(name, email, password, role)));

        assertEquals(expectedMessage, refused.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createUser_withATakenEmail_isRefusedWith409_andChangesNothing() {
        when(userRepository.existsByEmail("lisa@malmobygg.se")).thenReturn(true);

        // Annan stavning av samma adress räknas som upptagen, eftersom den normaliseras före kontrollen
        assertThrows(EmailAlreadyInUseException.class,
                () -> service.createUser(admin, request("Ny Lisa", " Lisa@MalmoBygg.se", PASSWORD, "INITIATOR")));

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    // Två admins skapar samma adress samtidigt: båda passerar existsByEmail, och databasens unikhetskrav stoppar
    // den som kommer sist. Det ska bli samma 409, inte ett 500.
    @Test
    void createUser_whenTheDatabaseRefusesADuplicate_isRefusedWith409_andWritesNoAudit() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(EmailAlreadyInUseException.class,
                () -> service.createUser(admin, request("Nils", "nils@x.se", PASSWORD, "INITIATOR")));

        verifyNoInteractions(auditService);
    }

    @Test
    void anAdminWithoutACompany_cannotListOrCreate() {
        AuthenticatedUserContext withoutCompany = new AuthenticatedUserContext(1L, null, Role.ADMIN);

        assertThrows(AccessDeniedException.class, () -> service.listUsers(withoutCompany));
        assertThrows(AccessDeniedException.class,
                () -> service.createUser(withoutCompany, request("A", "a@x.se", PASSWORD, "INITIATOR")));

        verifyNoInteractions(userRepository, auditService);
    }

    // --- Lista ---------------------------------------------------------------------------------------

    @Test
    void listUsers_asksForTheAdminsCompanyOnly_andReturnsNoPasswordHash() {
        User lisa = new User(TENANT, "Lisa Persson", "lisa@malmobygg.se", "hemlig-hash", Role.INITIATOR);
        User johan = new User(TENANT, "Johan Berg", "johan@malmobygg.se", "hemlig-hash", Role.ATTESTANT);
        ReflectionTestUtils.setField(lisa, "id", 1L);
        ReflectionTestUtils.setField(johan, "id", 2L);
        when(userRepository.findByTenantIdOrderByNameAscIdAsc(TENANT)).thenReturn(List.of(johan, lisa));

        List<UserResponse> users = service.listUsers(admin);

        assertEquals(List.of(
                new UserResponse(2L, "Johan Berg", "johan@malmobygg.se", Role.ATTESTANT),
                new UserResponse(1L, "Lisa Persson", "lisa@malmobygg.se", Role.INITIATOR)), users);
        assertFalse(users.toString().contains("hemlig-hash"));
        verify(userRepository).findByTenantIdOrderByNameAscIdAsc(TENANT);
        verify(userRepository, never()).findAll();
    }
}
