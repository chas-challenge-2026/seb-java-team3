package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.config.PasswordEncoderConfig;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Användaradministrationen (#242, ADR 0017): validering, företag från token, hashat lösenord, 409 på upptagen
// e-post och audit utan lösenord. Rollspärren (403 för andra än ADMIN) prövas på HTTP-nivå i controllertestet.
class UserAdminServiceTest {

    private static final Long TENANT = 7L;
    private static final String PASSWORD = "ett-langt-losenord";

    private final AuthenticatedUserContext admin = new AuthenticatedUserContext(99L, TENANT, Role.ADMIN);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();
    private final ApprovalStepRepository approvalStepRepository = mock(ApprovalStepRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final UserAdminService service = new UserAdminService(
            userRepository, approvalStepRepository, paymentRepository, refreshTokenRepository,
            passwordEncoder, auditService);

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
                        "Rollen ADMIN kan inte tilldelas här. Välj INITIATOR eller ATTESTANT."),
                Arguments.of("A", "a@x.se", valid, "admin",
                        "Rollen ADMIN kan inte tilldelas här. Välj INITIATOR eller ATTESTANT.")
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

    // --- Byta roll -----------------------------------------------------------------------------------

    private static User userWithId(Long id, Role role) {
        User user = new User(TENANT, "Lisa Persson", "lisa@malmobygg.se", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void givenInTheAdminsCompany(User user) {
        when(userRepository.findByIdAndTenantIdForUpdate(user.getId(), TENANT)).thenReturn(Optional.of(user));
    }

    @Test
    void changeRole_changesTheRole_andWritesAnAuditRow() {
        User lisa = userWithId(5L, Role.INITIATOR);
        givenInTheAdminsCompany(lisa);

        UserResponse response = service.changeRole(admin, 5L, new ChangeUserRoleRequest("ATTESTANT"));

        assertEquals(Role.ATTESTANT, lisa.getRole());
        assertEquals(new UserResponse(5L, "Lisa Persson", "lisa@malmobygg.se", Role.ATTESTANT), response);
        verify(auditService).record(TENANT, 99L, "UPDATE_USER_ROLE", "USER", 5L,
                "Roll ändrad för Lisa Persson (lisa@malmobygg.se): initiator till attestant");
        // En initiator har inga atteststeg att ta hänsyn till
        verifyNoInteractions(approvalStepRepository);
    }

    @Test
    void changeRole_attestantToInitiator_isAllowedWhenNoStepsWait() {
        User johan = userWithId(6L, Role.ATTESTANT);
        givenInTheAdminsCompany(johan);
        when(approvalStepRepository.existsByAttestantIdAndStatus(6L, ApprovalStepStatus.PENDING)).thenReturn(false);

        UserResponse response = service.changeRole(admin, 6L, new ChangeUserRoleRequest(" initiator "));

        assertEquals(Role.INITIATOR, johan.getRole());
        assertEquals(Role.INITIATOR, response.role());
        verify(auditService).record(eq(TENANT), eq(99L), eq("UPDATE_USER_ROLE"), eq("USER"), eq(6L),
                eq("Roll ändrad för Lisa Persson (lisa@malmobygg.se): attestant till initiator"));
    }

    @Test
    void changeRole_attestantWithWaitingSteps_isRefusedWith409_andChangesNothing() {
        User johan = userWithId(6L, Role.ATTESTANT);
        givenInTheAdminsCompany(johan);
        when(approvalStepRepository.existsByAttestantIdAndStatus(6L, ApprovalStepStatus.PENDING)).thenReturn(true);

        RoleChangeConflictException refused = assertThrows(RoleChangeConflictException.class,
                () -> service.changeRole(admin, 6L, new ChangeUserRoleRequest("INITIATOR")));

        assertEquals("Attestanten har väntande atteststeg. Låt dem godkännas eller avvisas innan rollen ändras.",
                refused.getMessage());
        assertEquals(Role.ATTESTANT, johan.getRole());
        verifyNoInteractions(auditService);
    }

    @Test
    void changeRole_toTheSameRole_succeedsWithoutChangeOrAudit() {
        User johan = userWithId(6L, Role.ATTESTANT);
        givenInTheAdminsCompany(johan);

        UserResponse response = service.changeRole(admin, 6L, new ChangeUserRoleRequest("ATTESTANT"));

        assertEquals(Role.ATTESTANT, response.role());
        assertEquals(Role.ATTESTANT, johan.getRole());
        verifyNoInteractions(auditService, approvalStepRepository);
    }

    // En administratörs roll ändras aldrig här, inte heller admins egen: ingen ska kunna ge sig själv eller
    // någon annan mer makt, eller ta bort den sista administratören.
    @Test
    void changeRole_ofAnAdministrator_isRefused_includingTheAdminsOwn() {
        User otherAdmin = userWithId(7L, Role.ADMIN);
        User theAdminThemselves = userWithId(99L, Role.ADMIN);
        givenInTheAdminsCompany(otherAdmin);
        givenInTheAdminsCompany(theAdminThemselves);

        for (User administrator : List.of(otherAdmin, theAdminThemselves)) {
            for (String newRole : List.of("INITIATOR", "ATTESTANT")) {
                UserAdminRefusedException refused = assertThrows(UserAdminRefusedException.class,
                        () -> service.changeRole(admin, administrator.getId(), new ChangeUserRoleRequest(newRole)));
                assertEquals("Rollen för en administratör kan inte ändras här.", refused.getMessage());
            }
            assertEquals(Role.ADMIN, administrator.getRole());
        }
        verifyNoInteractions(auditService, approvalStepRepository);
    }

    // Ett annat företags användare hittas inte av frågan, och ser därför ut som en som inte finns
    @Test
    void changeRole_ofAUserNotInTheAdminsCompany_looksLikeAMissingUser() {
        assertThrows(UserNotFoundException.class,
                () -> service.changeRole(admin, 5L, new ChangeUserRoleRequest("ATTESTANT")));

        verify(userRepository).findByIdAndTenantIdForUpdate(5L, TENANT);
        verify(userRepository, never()).findById(any());
        verifyNoInteractions(auditService, approvalStepRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "CHEF", "ADMIN", "admin"})
    void changeRole_withAnInvalidRole_isRefusedBeforeAnythingIsLookedUp(String role) {
        assertThrows(IllegalArgumentException.class,
                () -> service.changeRole(admin, 5L, new ChangeUserRoleRequest(role)));

        verifyNoInteractions(userRepository, approvalStepRepository, auditService);
    }

    @Test
    void changeRole_withoutARequestBody_isRefusedAsAMissingRole() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> service.changeRole(admin, 5L, null));

        assertEquals("Roll måste anges.", refused.getMessage());
    }

    @Test
    void changeRole_anAdminWithoutACompany_isRefused() {
        AuthenticatedUserContext withoutCompany = new AuthenticatedUserContext(1L, null, Role.ADMIN);

        assertThrows(AccessDeniedException.class,
                () -> service.changeRole(withoutCompany, 5L, new ChangeUserRoleRequest("ATTESTANT")));

        verifyNoInteractions(userRepository, auditService);
    }

    // --- Ta bort -------------------------------------------------------------------------------------

    @Test
    void deleteUser_removesAUserWithoutHistory_theirTokensFirst_andWritesAnAuditRow() {
        User lisa = userWithId(5L, Role.INITIATOR);
        givenInTheAdminsCompany(lisa);

        service.deleteUser(admin, 5L);

        InOrder order = inOrder(refreshTokenRepository, userRepository, auditService);
        order.verify(refreshTokenRepository).deleteAllForUser(5L);
        order.verify(userRepository).delete(lisa);
        order.verify(userRepository).flush();
        order.verify(auditService).record(TENANT, 99L, "DELETE_USER", "USER", 5L,
                "Användare borttagen: Lisa Persson (lisa@malmobygg.se), roll initiator");
    }

    @Test
    void deleteUser_refusesAUserWhoHasCreatedAPayment_andChangesNothing() {
        User lisa = userWithId(5L, Role.INITIATOR);
        givenInTheAdminsCompany(lisa);
        when(paymentRepository.existsByCreatedBy(5L)).thenReturn(true);

        assertDeleteRefusedAndNothingChanged(lisa);
    }

    @Test
    void deleteUser_refusesAnAttestantWhoHasBeenAssignedAStep_evenAnAlreadyDecidedOne() {
        User johan = userWithId(6L, Role.ATTESTANT);
        givenInTheAdminsCompany(johan);
        when(approvalStepRepository.existsByAttestantId(6L)).thenReturn(true);

        assertDeleteRefusedAndNothingChanged(johan);
    }

    @Test
    void deleteUser_refusesAUserWhoHasDecidedAStep() {
        User johan = userWithId(6L, Role.ATTESTANT);
        givenInTheAdminsCompany(johan);
        when(approvalStepRepository.existsByDecidedBy(6L)).thenReturn(true);

        assertDeleteRefusedAndNothingChanged(johan);
    }

    private void assertDeleteRefusedAndNothingChanged(User user) {
        UserDeleteConflictException refused = assertThrows(UserDeleteConflictException.class,
                () -> service.deleteUser(admin, user.getId()));

        assertEquals(UserAdminService.DELETE_BLOCKED_BY_HISTORY, refused.getMessage());
        verify(userRepository, never()).delete(any());
        verifyNoInteractions(refreshTokenRepository, auditService);
    }

    // Databasens främmande nycklar är sista skyddet om något hann kopplas till användaren efter kontrollen
    @Test
    void deleteUser_whenTheDatabaseRefusesTheDelete_isRefusedWith409_andWritesNoAudit() {
        User lisa = userWithId(5L, Role.INITIATOR);
        givenInTheAdminsCompany(lisa);
        doThrow(new DataIntegrityViolationException("fk")).when(userRepository).flush();

        assertThrows(UserDeleteConflictException.class, () -> service.deleteUser(admin, 5L));

        verifyNoInteractions(auditService);
    }

    @Test
    void deleteUser_ofAnAdministrator_isRefused_includingTheAdminsOwn() {
        User otherAdmin = userWithId(7L, Role.ADMIN);
        User theAdminThemselves = userWithId(99L, Role.ADMIN);
        givenInTheAdminsCompany(otherAdmin);
        givenInTheAdminsCompany(theAdminThemselves);

        for (User administrator : List.of(otherAdmin, theAdminThemselves)) {
            UserAdminRefusedException refused = assertThrows(UserAdminRefusedException.class,
                    () -> service.deleteUser(admin, administrator.getId()));
            assertEquals("En administratör kan inte tas bort.", refused.getMessage());
        }
        verify(userRepository, never()).delete(any());
        verifyNoInteractions(refreshTokenRepository, auditService, paymentRepository, approvalStepRepository);
    }

    // Ett annat företags användare hittas inte av frågan, och ser därför ut som en som inte finns
    @Test
    void deleteUser_ofAUserNotInTheAdminsCompany_looksLikeAMissingUser() {
        assertThrows(UserNotFoundException.class, () -> service.deleteUser(admin, 5L));

        verify(userRepository).findByIdAndTenantIdForUpdate(5L, TENANT);
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).delete(any());
        verifyNoInteractions(refreshTokenRepository, auditService);
    }

    @Test
    void deleteUser_anAdminWithoutACompany_isRefused() {
        AuthenticatedUserContext withoutCompany = new AuthenticatedUserContext(1L, null, Role.ADMIN);

        assertThrows(AccessDeniedException.class, () -> service.deleteUser(withoutCompany, 5L));

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
