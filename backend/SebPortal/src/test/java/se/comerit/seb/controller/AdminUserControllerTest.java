package se.comerit.seb.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.ChangeUserRoleRequest;
import se.comerit.seb.dto.CreateUserRequest;
import se.comerit.seb.dto.UserResponse;
import se.comerit.seb.exception.EmailAlreadyInUseException;
import se.comerit.seb.exception.RoleChangeConflictException;
import se.comerit.seb.exception.UserAdminRefusedException;
import se.comerit.seb.exception.UserDeleteConflictException;
import se.comerit.seb.exception.UserNotFoundException;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.RoleAccessDeniedHandler;
import se.comerit.seb.service.UserAdminService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controllern och rollspärren för användaradministrationen (#242, ADR 0017), genom den riktiga
// säkerhetskedjan med en riktig token. Tjänsten är mockad: hela kedjan prövas i
// UserAdministrationIntegrationTest.
@WebMvcTest(AdminUserController.class)
@Import({JwtSecurityConfig.class, RoleAccessDeniedHandler.class, JwtService.class, JwtUserContext.class})
class AdminUserControllerTest {

    private static final Long ADMIN_ID = 9L;
    private static final Long TENANT = 7L;
    private static final String CREATE_BODY = """
            {"name":"Nils Ek","email":"nils@malmobygg.se","password":"ett-langt-losenord","role":"ATTESTANT"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserAdminService userAdminService;

    private String bearer(Role role) {
        return "Bearer " + jwtService.generateToken(new AuthenticatedUserContext(ADMIN_ID, TENANT, role));
    }

    @Test
    void list_asAdmin_returns200_andPassesTheCompanyFromTheToken() throws Exception {
        when(userAdminService.listUsers(any())).thenReturn(
                List.of(new UserResponse(1L, "Lisa Persson", "lisa@malmobygg.se", Role.INITIATOR)));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Lisa Persson"))
                .andExpect(jsonPath("$[0].email").value("lisa@malmobygg.se"))
                .andExpect(jsonPath("$[0].role").value("INITIATOR"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());

        ArgumentCaptor<AuthenticatedUserContext> admin = ArgumentCaptor.forClass(AuthenticatedUserContext.class);
        verify(userAdminService).listUsers(admin.capture());
        assertEquals(new AuthenticatedUserContext(ADMIN_ID, TENANT, Role.ADMIN), admin.getValue());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"INITIATOR", "ATTESTANT"})
    void list_asAnyOtherRole_returns403_andNeverReachesTheService(Role role) throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAdminService);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"INITIATOR", "ATTESTANT"})
    void create_asAnyOtherRole_returns403_andNeverReachesTheService(Role role) throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(role))
                        .contentType(APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAdminService);
    }

    @Test
    void withoutAToken_orWithAnInvalidOne_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/users").contentType(APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer inte.en.token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAdminService);
    }

    @Test
    void create_asAdmin_returns201WithTheNewUser_andIgnoresACompanyIdInTheRequest() throws Exception {
        when(userAdminService.createUser(any(), any()))
                .thenReturn(new UserResponse(5L, "Nils Ek", "nils@malmobygg.se", Role.ATTESTANT));

        // tenantId i anropet är ett försök att skapa användaren hos ett annat företag
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Nils Ek","email":"nils@malmobygg.se","password":"ett-langt-losenord",
                                 "role":"ATTESTANT","tenantId":999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Nils Ek"))
                .andExpect(jsonPath("$.email").value("nils@malmobygg.se"))
                .andExpect(jsonPath("$.role").value("ATTESTANT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<AuthenticatedUserContext> admin = ArgumentCaptor.forClass(AuthenticatedUserContext.class);
        ArgumentCaptor<CreateUserRequest> request = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userAdminService).createUser(admin.capture(), request.capture());
        // Företaget kommer från token, och förfrågan har inget fält som kan bära ett annat
        assertEquals(TENANT, admin.getValue().tenantId());
        assertEquals(new CreateUserRequest("Nils Ek", "nils@malmobygg.se", "ett-langt-losenord", "ATTESTANT"),
                request.getValue());
    }

    @Test
    void create_whenTheServiceRefusesTheInput_returns400WithTheMessage() throws Exception {
        when(userAdminService.createUser(any(), any()))
                .thenThrow(new IllegalArgumentException("Lösenordet måste vara minst 12 tecken."));

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Lösenordet måste vara minst 12 tecken."));
    }

    @Test
    void create_whenTheEmailIsTaken_returns409WithTheMessage() throws Exception {
        when(userAdminService.createUser(any(), any())).thenThrow(new EmailAlreadyInUseException());

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("E-postadressen används redan."));
    }

    // --- Byta roll -----------------------------------------------------------------------------------

    private static final String ROLE_BODY = "{\"role\":\"ATTESTANT\"}";

    @Test
    void changeRole_asAdmin_returns200WithTheUser_andPassesTheIdTheRequestAndTheCompany() throws Exception {
        when(userAdminService.changeRole(any(), any(), any()))
                .thenReturn(new UserResponse(5L, "Lisa Persson", "lisa@malmobygg.se", Role.ATTESTANT));

        mockMvc.perform(patch("/api/admin/users/{userId}/role", 5L)
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(ROLE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("ATTESTANT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<AuthenticatedUserContext> admin = ArgumentCaptor.forClass(AuthenticatedUserContext.class);
        ArgumentCaptor<ChangeUserRoleRequest> request = ArgumentCaptor.forClass(ChangeUserRoleRequest.class);
        verify(userAdminService).changeRole(admin.capture(), eq(5L), request.capture());
        assertEquals(new AuthenticatedUserContext(ADMIN_ID, TENANT, Role.ADMIN), admin.getValue());
        assertEquals(new ChangeUserRoleRequest("ATTESTANT"), request.getValue());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"INITIATOR", "ATTESTANT"})
    void changeRole_asAnyOtherRole_returns403_andNeverReachesTheService(Role role) throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/role", 5L)
                        .header("Authorization", bearer(role))
                        .contentType(APPLICATION_JSON)
                        .content(ROLE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAdminService);
    }

    @Test
    void changeRole_withoutAToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/role", 5L)
                        .contentType(APPLICATION_JSON)
                        .content(ROLE_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAdminService);
    }

    @Test
    void changeRole_whenTheUserIsNotInTheCompany_returns404() throws Exception {
        when(userAdminService.changeRole(any(), any(), any())).thenThrow(new UserNotFoundException());

        mockMvc.perform(patch("/api/admin/users/{userId}/role", 5L)
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(ROLE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Användaren finns inte."));
    }

    @Test
    void changeRole_whenTheTargetIsAnAdministrator_returns403WithTheMessage() throws Exception {
        when(userAdminService.changeRole(any(), any(), any()))
                .thenThrow(new UserAdminRefusedException("Rollen för en administratör kan inte ändras här."));

        mockMvc.perform(patch("/api/admin/users/{userId}/role", 5L)
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(ROLE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Rollen för en administratör kan inte ändras här."));
    }

    @Test
    void changeRole_whenAnAttestantHasWaitingSteps_returns409WithTheMessage() throws Exception {
        when(userAdminService.changeRole(any(), any(), any()))
                .thenThrow(new RoleChangeConflictException("Attestanten har väntande atteststeg."));

        mockMvc.perform(patch("/api/admin/users/{userId}/role", 5L)
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(ROLE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Attestanten har väntande atteststeg."));
    }

    @Test
    void changeRole_whenTheServiceRefusesTheRole_returns400WithTheMessage() throws Exception {
        when(userAdminService.changeRole(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Okänd roll. Välj INITIATOR eller ATTESTANT."));

        mockMvc.perform(patch("/api/admin/users/{userId}/role", 5L)
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content("{\"role\":\"CHEF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Okänd roll. Välj INITIATOR eller ATTESTANT."));
    }

    @Test
    void changeRole_withANonNumericId_orNoBody_returns400_andNeverReachesTheService() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/role", "abc")
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(ROLE_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/users/{userId}/role", 5L)
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userAdminService);
    }

    // --- Ta bort -------------------------------------------------------------------------------------

    @Test
    void delete_asAdmin_returns204WithoutBody_andPassesTheIdAndTheCompany() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{userId}", 5L).header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        ArgumentCaptor<AuthenticatedUserContext> admin = ArgumentCaptor.forClass(AuthenticatedUserContext.class);
        verify(userAdminService).deleteUser(admin.capture(), eq(5L));
        assertEquals(new AuthenticatedUserContext(ADMIN_ID, TENANT, Role.ADMIN), admin.getValue());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"INITIATOR", "ATTESTANT"})
    void delete_asAnyOtherRole_returns403_andNeverReachesTheService(Role role) throws Exception {
        mockMvc.perform(delete("/api/admin/users/{userId}", 5L).header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAdminService);
    }

    @Test
    void delete_withoutAToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{userId}", 5L))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAdminService);
    }

    @Test
    void delete_whenTheUserIsNotInTheCompany_returns404() throws Exception {
        doThrow(new UserNotFoundException()).when(userAdminService).deleteUser(any(), any());

        mockMvc.perform(delete("/api/admin/users/{userId}", 5L).header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Användaren finns inte."));
    }

    @Test
    void delete_whenTheTargetIsAnAdministrator_returns403WithTheMessage() throws Exception {
        doThrow(new UserAdminRefusedException("En administratör kan inte tas bort."))
                .when(userAdminService).deleteUser(any(), any());

        mockMvc.perform(delete("/api/admin/users/{userId}", 5L).header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("En administratör kan inte tas bort."));
    }

    @Test
    void delete_whenTheUserHasHistory_returns409WithTheMessage() throws Exception {
        doThrow(new UserDeleteConflictException("Användaren har betalningar kopplade till sig."))
                .when(userAdminService).deleteUser(any(), any());

        mockMvc.perform(delete("/api/admin/users/{userId}", 5L).header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Användaren har betalningar kopplade till sig."));
    }

    @Test
    void delete_withANonNumericId_returns400_andNeverReachesTheService() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{userId}", "abc").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userAdminService);
    }

    @Test
    void create_withoutABody_returns400_andNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(Role.ADMIN))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userAdminService);
    }
}
