package se.comerit.seb.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Användaradministrationen genom hela kedjan (#242, ADR 0017): säkerhetskedjan, controller, service, repository
// och audit, mot H2 i minnet utan Docker. Bara signeringen av audit-loggen är avstängd (inget native bibliotek).
// Två företag finns med, så att isoleringen mellan dem går att visa.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-administration-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "audit.signing-required=false",
        "audit.log-path="
})
@AutoConfigureMockMvc
class UserAdministrationIntegrationTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;
    private static final String PASSWORD = "ett-langt-losenord";

    // BCrypt med kostnad 12 tar en kvarts sekund, så seed-användarna delar en hash
    private static String sharedHash;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User adminA;
    private User attestantA;
    private User initiatorA;
    private User adminB;

    @BeforeEach
    void seedTwoCompanies() {
        jdbc.update("DELETE FROM approval_steps");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM audit_entries");
        jdbc.update("DELETE FROM users");

        adminA = save(COMPANY_A, "Sara Ek", "sara@malmobygg.se", Role.ADMIN);
        attestantA = save(COMPANY_A, "Johan Berg", "johan@malmobygg.se", Role.ATTESTANT);
        initiatorA = save(COMPANY_A, "Lisa Persson", "lisa@malmobygg.se", Role.INITIATOR);
        adminB = save(COMPANY_B, "Anna Lund", "anna@lundskonsult.se", Role.ADMIN);
        save(COMPANY_B, "Erik Lund", "erik@lundskonsult.se", Role.INITIATOR);
    }

    private User save(Long tenantId, String name, String email, Role role) {
        if (sharedHash == null) {
            sharedHash = passwordEncoder.encode(PASSWORD);
        }
        return userRepository.saveAndFlush(new User(tenantId, name, email, sharedHash, role));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(
                new AuthenticatedUserContext(user.getId(), user.getTenantId(), user.getRole()));
    }

    // Svaren läses som UTF-8, som MockMvcs egna jsonPath-kontroller gör. getContentAsString() utan argument
    // läser JSON som ISO-8859-1 och förvränger å, ä och ö.
    private ResultActions listAs(User user) throws Exception {
        return mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(user)));
    }

    private ResultActions createAs(User user, String json) throws Exception {
        return mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(user))
                .contentType(APPLICATION_JSON)
                .content(json));
    }

    private static String newUser(String email, String role) {
        return """
                {"name":"Nils Ek","email":"%s","password":"%s","role":"%s"}
                """.formatted(email, PASSWORD, role);
    }

    private long userCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    private long createUserAuditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_entries WHERE action = 'CREATE_USER'", Long.class);
    }

    // --- Åtkomst -------------------------------------------------------------------------------------

    @Test
    void onlyAnAdmin_mayListOrCreate_andARefusedCallChangesNothing() throws Exception {
        for (User notAdmin : List.of(attestantA, initiatorA)) {
            listAs(notAdmin).andExpect(status().isForbidden());
            createAs(notAdmin, newUser("nils@malmobygg.se", "ATTESTANT")).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/users").contentType(APPLICATION_JSON)
                .content(newUser("nils@malmobygg.se", "ATTESTANT"))).andExpect(status().isUnauthorized());

        assertEquals(5, userCount());
        assertEquals(0, createUserAuditCount());
    }

    // --- Företagsisolering ---------------------------------------------------------------------------

    @Test
    void anAdmin_seesOnlyTheirOwnCompany_andNeverAPasswordHash() throws Exception {
        String bodyA = listAs(adminA).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
        String bodyB = listAs(adminB).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);

        assertEquals(List.of("Johan Berg", "Lisa Persson", "Sara Ek"), JsonPath.read(bodyA, "$[*].name"));
        assertEquals(List.of("Anna Lund", "Erik Lund"), JsonPath.read(bodyB, "$[*].name"));

        // Bara id, namn, e-post och roll, och ingen hash i kroppen
        for (Map<String, Object> user : JsonPath.<List<Map<String, Object>>>read(bodyA, "$")) {
            assertEquals(List.of("email", "id", "name", "role"), user.keySet().stream().sorted().toList());
        }
        assertFalse(bodyA.contains("$2a$") || bodyA.contains("$2b$"));
    }

    @Test
    void aNewUser_alwaysBelongsToTheAdminsCompany_whateverTheRequestSays() throws Exception {
        // tenantId i anropet är ett försök att skapa användaren hos det andra företaget
        createAs(adminA, """
                {"name":"Nils Ek","email":"nils@malmobygg.se","password":"%s","role":"ATTESTANT","tenantId":2}
                """.formatted(PASSWORD)).andExpect(status().isCreated());

        assertEquals(COMPANY_A, jdbc.queryForObject(
                "SELECT tenant_id FROM users WHERE email = 'nils@malmobygg.se'", Long.class));
        assertTrue(JsonPath.<List<String>>read(
                listAs(adminA).andReturn().getResponse().getContentAsString(UTF_8), "$[*].email")
                .contains("nils@malmobygg.se"));
        assertFalse(JsonPath.<List<String>>read(
                listAs(adminB).andReturn().getResponse().getContentAsString(UTF_8), "$[*].email")
                .contains("nils@malmobygg.se"));
    }

    // --- Skapa ---------------------------------------------------------------------------------------

    @Test
    void aCreatedUser_getsAHashedPassword_andCanLogIn() throws Exception {
        String created = createAs(adminA, newUser("  Nils.Ek@MalmoBygg.SE ", "ATTESTANT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nils.ek@malmobygg.se"))
                .andExpect(jsonPath("$.role").value("ATTESTANT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString(UTF_8);
        int id = JsonPath.read(created, "$.id");

        // Sparat som en BCrypt-hash med kostnad 12, aldrig lösenordet
        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
        assertNotEquals(PASSWORD, stored);
        assertTrue(stored.startsWith("$2a$12$"));
        assertFalse(stored.contains(PASSWORD));

        // Inloggning med annan stavning av adressen, och med rätt lösenord
        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":" NILS.EK@malmobygg.se","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ATTESTANT"))
                .andReturn().getResponse().getContentAsString(UTF_8);
        String token = JsonPath.read(login, "$.token");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nils.ek@malmobygg.se"))
                .andExpect(jsonPath("$.role").value("ATTESTANT"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nils.ek@malmobygg.se\",\"password\":\"fel-losenord-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    // E-postadressen är unik i hela databasen (V1), inte per företag. En dubblett hos det andra företaget ger
    // därför också 409. Konsekvens: en admin kan avgöra om en adress finns hos ett annat företag.
    @Test
    void aTakenEmail_isRefusedWith409_whateverTheCasing_andEvenIfItBelongsToAnotherCompany() throws Exception {
        for (String taken : List.of("sara@malmobygg.se", "SARA@MalmoBygg.se", " johan@malmobygg.se", "erik@lundskonsult.se")) {
            createAs(adminA, newUser(taken, "INITIATOR"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("E-postadressen används redan."));
        }

        assertEquals(5, userCount());
        assertEquals(0, createUserAuditCount());
    }

    @Test
    void invalidInput_isRefusedWith400AndAMessage_andChangesNothing() throws Exception {
        Map<String, String> bodyToMessage = Map.of(
                newUser("nils@malmobygg.se", "ADMIN"),
                "Rollen ADMIN kan inte tilldelas här. Välj INITIATOR eller ATTESTANT.",
                newUser("nils@malmobygg.se", "CHEF"),
                "Okänd roll. Välj INITIATOR eller ATTESTANT.",
                newUser("inte-en-adress", "ATTESTANT"),
                "Ange en giltig e-postadress.",
                """
                {"name":"Nils Ek","email":"nils@malmobygg.se","password":"kort","role":"ATTESTANT"}
                """,
                "Lösenordet måste vara minst 12 tecken.",
                """
                {"email":"nils@malmobygg.se","password":"ett-langt-losenord","role":"ATTESTANT"}
                """,
                "Namn måste anges.");

        for (Map.Entry<String, String> refused : bodyToMessage.entrySet()) {
            createAs(adminA, refused.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(refused.getValue()));
        }

        assertEquals(5, userCount());
        assertEquals(0, createUserAuditCount());
    }

    // --- Audit ---------------------------------------------------------------------------------------

    @Test
    void creatingAUser_isLoggedForTheAdminOnly_withoutThePassword() throws Exception {
        String created = createAs(adminA, newUser("nils@malmobygg.se", "ATTESTANT"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(UTF_8);
        int id = JsonPath.read(created, "$.id");

        String adminLog = mockMvc.perform(get("/api/audit").header("Authorization", bearer(adminA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
        List<Map<String, Object>> entries = JsonPath.read(adminLog, "$[?(@.action=='CREATE_USER')]");

        assertEquals(1, entries.size());
        Map<String, Object> entry = entries.get(0);
        assertEquals("USER", entry.get("entityType"));
        assertEquals(id, ((Number) entry.get("entityId")).intValue());
        assertEquals("Sara Ek", entry.get("userName"));
        assertEquals("Användare skapad: Nils Ek (nils@malmobygg.se), roll attestant", entry.get("description"));
        assertFalse(adminLog.contains(PASSWORD));
        assertFalse(adminLog.contains("$2a$"));

        // Attestanten ser bara skapa, godkänn och avvisa betalning. Det andra företagets admin ser ingenting.
        String attestantLog = mockMvc.perform(get("/api/audit").header("Authorization", bearer(attestantA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
        assertTrue(JsonPath.<List<Object>>read(attestantLog, "$[?(@.action=='CREATE_USER')]").isEmpty());

        String otherCompanyLog = mockMvc.perform(get("/api/audit").header("Authorization", bearer(adminB)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
        assertTrue(JsonPath.<List<Object>>read(otherCompanyLog, "$[?(@.action=='CREATE_USER')]").isEmpty());
    }

    // --- Byta roll -----------------------------------------------------------------------------------

    private ResultActions changeRoleAs(User actor, Number userId, String role) throws Exception {
        return mockMvc.perform(patch("/api/admin/users/{userId}/role", userId)
                .header("Authorization", bearer(actor))
                .contentType(APPLICATION_JSON)
                .content("{\"role\":\"%s\"}".formatted(role)));
    }

    private String roleInDatabase(User user) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, user.getId());
    }

    private long auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_entries WHERE action = ?", Long.class, action);
    }

    @Test
    void changingARole_updatesTheUser_logsIt_andFollowsTheUserToTheNextLogin() throws Exception {
        changeRoleAs(adminA, initiatorA.getId(), "ATTESTANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(initiatorA.getId().intValue()))
                .andExpect(jsonPath("$.role").value("ATTESTANT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertEquals("attestant", roleInDatabase(initiatorA));

        String adminLog = mockMvc.perform(get("/api/audit").header("Authorization", bearer(adminA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
        List<Map<String, Object>> entries = JsonPath.read(adminLog, "$[?(@.action=='UPDATE_USER_ROLE')]");
        assertEquals(1, entries.size());
        assertEquals("USER", entries.get(0).get("entityType"));
        assertEquals(initiatorA.getId().intValue(), ((Number) entries.get(0).get("entityId")).intValue());
        assertEquals("Sara Ek", entries.get(0).get("userName"));
        assertEquals("Roll ändrad för Lisa Persson (lisa@malmobygg.se): initiator till attestant",
                entries.get(0).get("description"));

        // Attestanten ser inte administratörens handlingar
        String attestantLog = mockMvc.perform(get("/api/audit").header("Authorization", bearer(attestantA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
        assertTrue(JsonPath.<List<Object>>read(attestantLog, "$[?(@.action=='UPDATE_USER_ROLE')]").isEmpty());

        // Rollen följer med vid nästa inloggning
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"lisa@malmobygg.se\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ATTESTANT"));
    }

    @Test
    void aUserOfAnotherCompany_looksExactlyLikeAMissingUser_andIsLeftAlone() throws Exception {
        long erikId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'erik@lundskonsult.se'", Long.class);

        String other = changeRoleAs(adminA, erikId, "ATTESTANT")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(UTF_8);
        String missing = changeRoleAs(adminA, 999_999, "ATTESTANT")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(UTF_8);

        assertEquals("Användaren finns inte.", JsonPath.<String>read(other, "$.message"));
        assertEquals(JsonPath.<String>read(missing, "$.message"), JsonPath.<String>read(other, "$.message"));
        assertEquals("initiator", jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, erikId));
        assertEquals(0, auditCount("UPDATE_USER_ROLE"));
    }

    // Ingen kan ge sig själv eller någon annan mer makt, eller ta bort den sista administratören
    @Test
    void anAdministratorsRole_cannotBeChanged_notEvenTheAdminsOwn() throws Exception {
        User secondAdmin = save(COMPANY_A, "Nora Ek", "nora@malmobygg.se", Role.ADMIN);

        for (User target : List.of(adminA, secondAdmin)) {
            changeRoleAs(adminA, target.getId(), "INITIATOR")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Rollen för en administratör kan inte ändras här."));
            assertEquals("admin", roleInDatabase(target));
        }
        assertEquals(0, auditCount("UPDATE_USER_ROLE"));
    }

    @Test
    void theAdminRole_cannotBeHandedOut_andAnUnknownRoleIsRefused() throws Exception {
        changeRoleAs(adminA, initiatorA.getId(), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Rollen ADMIN kan inte tilldelas här. Välj INITIATOR eller ATTESTANT."));
        changeRoleAs(adminA, initiatorA.getId(), "CHEF")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Okänd roll. Välj INITIATOR eller ATTESTANT."));

        assertEquals("initiator", roleInDatabase(initiatorA));
        assertEquals(0, auditCount("UPDATE_USER_ROLE"));
    }

    @Test
    void anAttestantWithWaitingSteps_cannotBecomeAnInitiator_untilTheyAreDecided() throws Exception {
        Payment payment = new Payment(COMPANY_A, 1L, "SE8550000000054910000003",
                new BigDecimal("7500.00"), "Faktura", initiatorA.getId());
        payment.addApprovalStep(new ApprovalStep(attestantA.getId(), 1));
        paymentRepository.saveAndFlush(payment);

        changeRoleAs(adminA, attestantA.getId(), "INITIATOR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Attestanten har väntande atteststeg. Låt dem godkännas eller avvisas innan rollen ändras."));
        assertEquals("attestant", roleInDatabase(attestantA));
        assertEquals(0, auditCount("UPDATE_USER_ROLE"));

        // När steget är avgjort går rolländringen igenom
        jdbc.update("UPDATE approval_steps SET status = 'approved'");
        changeRoleAs(adminA, attestantA.getId(), "INITIATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INITIATOR"));
        assertEquals("initiator", roleInDatabase(attestantA));
        assertEquals(1, auditCount("UPDATE_USER_ROLE"));
    }

    @Test
    void theSameRole_isANoOp_withoutAnAuditRow() throws Exception {
        changeRoleAs(adminA, attestantA.getId(), "ATTESTANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ATTESTANT"));

        assertEquals("attestant", roleInDatabase(attestantA));
        assertEquals(0, auditCount("UPDATE_USER_ROLE"));
    }

    // --- Ta bort -------------------------------------------------------------------------------------

    private ResultActions deleteAs(User actor, Number userId) throws Exception {
        return mockMvc.perform(delete("/api/admin/users/{userId}", userId)
                .header("Authorization", bearer(actor)));
    }

    private long usersWithId(Number id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, id);
    }

    private ResultActions loginAsLisa() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"lisa@malmobygg.se\",\"password\":\"%s\"}".formatted(PASSWORD)));
    }

    @Test
    void deletingAUserWithoutHistory_removesThem_theirSessions_andLogsIt() throws Exception {
        loginAsLisa().andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Long.class, initiatorA.getId()));

        deleteAs(adminA, initiatorA.getId()).andExpect(status().isNoContent());

        assertEquals(0, usersWithId(initiatorA.getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Long.class, initiatorA.getId()));
        // Den borttagna kan inte logga in, och dyker inte upp i listan
        loginAsLisa().andExpect(status().isUnauthorized());
        listAs(adminA).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        String adminLog = mockMvc.perform(get("/api/audit").header("Authorization", bearer(adminA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
        List<Map<String, Object>> entries = JsonPath.read(adminLog, "$[?(@.action=='DELETE_USER')]");
        assertEquals(1, entries.size());
        assertEquals("USER", entries.get(0).get("entityType"));
        assertEquals("Sara Ek", entries.get(0).get("userName"));
        assertEquals("Användare borttagen: Lisa Persson (lisa@malmobygg.se), roll initiator",
                entries.get(0).get("description"));
    }

    // Historiken (vem som skapade och godkände vad) måste bevaras, så den som är kopplad till en betalning
    // eller ett steg tas inte bort
    @Test
    void aUserWithHistory_cannotBeDeleted_andIsLeftAlone() throws Exception {
        Payment payment = new Payment(COMPANY_A, 1L, "SE8550000000054910000003",
                new BigDecimal("7500.00"), "Faktura", initiatorA.getId());
        payment.addApprovalStep(new ApprovalStep(attestantA.getId(), 1));
        paymentRepository.saveAndFlush(payment);

        // Initiatorn har skapat betalningen, och attestanten är tilldelad steget (väntande)
        for (User target : List.of(initiatorA, attestantA)) {
            deleteAs(adminA, target.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Användaren har betalningar eller atteststeg kopplade till sig och kan inte tas bort, "
                                    + "eftersom historiken måste bevaras."));
            assertEquals(1, usersWithId(target.getId()));
        }

        // Också när steget är avgjort och bara historiken återstår
        jdbc.update("UPDATE approval_steps SET status = 'approved', decided_by = ?", attestantA.getId());
        deleteAs(adminA, attestantA.getId()).andExpect(status().isConflict());
        assertEquals(1, usersWithId(attestantA.getId()));
        assertEquals(0, auditCount("DELETE_USER"));
    }

    @Test
    void anAdministrator_cannotBeDeleted_notEvenTheAdminThemselves() throws Exception {
        User secondAdmin = save(COMPANY_A, "Nora Ek", "nora@malmobygg.se", Role.ADMIN);

        for (User target : List.of(adminA, secondAdmin)) {
            deleteAs(adminA, target.getId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("En administratör kan inte tas bort."));
            assertEquals(1, usersWithId(target.getId()));
        }
        assertEquals(0, auditCount("DELETE_USER"));
    }

    @Test
    void aUserOfAnotherCompany_looksLikeAMissingUserWhenDeleted_andIsLeftAlone() throws Exception {
        long erikId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'erik@lundskonsult.se'", Long.class);

        String other = deleteAs(adminA, erikId)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(UTF_8);
        String missing = deleteAs(adminA, 999_999)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(UTF_8);

        assertEquals(JsonPath.<String>read(missing, "$.message"), JsonPath.<String>read(other, "$.message"));
        assertEquals(1, usersWithId(erikId));
        assertEquals(0, auditCount("DELETE_USER"));
    }

    @Test
    void onlyAnAdmin_mayDeleteAUser() throws Exception {
        for (User notAdmin : List.of(attestantA, initiatorA)) {
            deleteAs(notAdmin, initiatorA.getId()).andExpect(status().isForbidden());
        }
        mockMvc.perform(delete("/api/admin/users/{userId}", initiatorA.getId()))
                .andExpect(status().isUnauthorized());

        assertEquals(1, usersWithId(initiatorA.getId()));
        assertEquals(0, auditCount("DELETE_USER"));
    }

    @Test
    void onlyAnAdmin_mayChangeARole() throws Exception {
        for (User notAdmin : List.of(attestantA, initiatorA)) {
            changeRoleAs(notAdmin, initiatorA.getId(), "ATTESTANT").andExpect(status().isForbidden());
        }
        mockMvc.perform(patch("/api/admin/users/{userId}/role", initiatorA.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"role\":\"ATTESTANT\"}"))
                .andExpect(status().isUnauthorized());

        assertEquals("initiator", roleInDatabase(initiatorA));
        assertEquals(0, auditCount("UPDATE_USER_ROLE"));
    }
}
