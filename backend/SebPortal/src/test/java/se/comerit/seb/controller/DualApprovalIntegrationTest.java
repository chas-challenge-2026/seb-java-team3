package se.comerit.seb.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import se.comerit.seb.domain.Role;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// ADR 0016: betalningar över 50 000 kr kräver två olika attestanter, i tur och ordning. Hela kedjan körs:
// riktig JWT -> filter -> @PreAuthorize -> controller -> service -> repository -> Postgres 12 med Flyway.
// Inget är mockat, eftersom frågorna bakom attestkorgen är en del av det som testas: de måste hämta
// betalningens alla steg, annars går det inte att se vilket steg som är på tur.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@Testcontainers
class DualApprovalIntegrationTest {

    // Klarar MOD97
    private static final String TO_IBAN = "SE4550000000058398257466";
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("200000.00");
    private static final BigDecimal OVER_TWO_ATTESTANT_THRESHOLD = new BigDecimal("50000.01");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:12");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long tenantId;
    private long account;
    private long initiator;
    private long firstAttestant;
    private long secondAttestant;
    private long admin;
    private String initiatorToken;
    private String firstAttestantToken;
    private String secondAttestantToken;
    private String adminToken;

    @BeforeEach
    void seedCompany() {
        jdbc.execute("""
                TRUNCATE TABLE refresh_tokens, audit_entries, approval_steps, payments, accounts, users, tenants
                RESTART IDENTITY CASCADE
                """);

        tenantId = jdbc.queryForObject("INSERT INTO tenants (name) VALUES ('Bolaget AB') RETURNING id", Long.class);
        initiator = user(tenantId, "Ina Initiatör", "initiator", "initiator");
        firstAttestant = user(tenantId, "Första Attestant", "first", "attestant");
        secondAttestant = user(tenantId, "Andra Attestant", "second", "attestant");
        admin = user(tenantId, "Ada Admin", "admin", "admin");
        account = account(tenantId);

        initiatorToken = token(initiator, tenantId, Role.INITIATOR);
        firstAttestantToken = token(firstAttestant, tenantId, Role.ATTESTANT);
        secondAttestantToken = token(secondAttestant, tenantId, Role.ATTESTANT);
        adminToken = token(admin, tenantId, Role.ADMIN);
    }

    @Test
    void exactlyTheThreshold_needsOneAttestant_andOneOreMoreNeedsTwoDifferent() throws Exception {
        long atThreshold = createPayment(new BigDecimal("50000.00"), initiatorToken);
        long overThreshold = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);

        assertEquals(List.of(firstAttestant), assignedAttestants(atThreshold));
        assertEquals(List.of(firstAttestant, secondAttestant), assignedAttestants(overThreshold));
    }

    @Test
    void paymentIsExecutedOnlyWhenBothAttestantsHaveApproved_inOrder() throws Exception {
        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);
        long step1 = stepId(payment, 1);
        long step2 = stepId(payment, 2);

        // Steg 2 syns inte, räknas inte och går inte att godkänna förrän steg 1 är klart
        assertEquals(List.of(step1), queue(firstAttestantToken));
        assertEquals(1L, count(firstAttestantToken));
        assertEquals(List.of(), queue(secondAttestantToken));
        assertEquals(0L, count(secondAttestantToken));
        assertEquals(409, perform(post("/api/approvals/" + step2 + "/approve"), secondAttestantToken).status());

        assertEquals(204, perform(post("/api/approvals/" + step1 + "/approve"), firstAttestantToken).status());

        // Efter första godkännandet: pengarna ligger kvar, och turen har gått vidare
        assertEquals("pending_approval", paymentStatus(payment));
        assertEquals(0, STARTING_BALANCE.compareTo(balance()));
        assertNull(jdbc.queryForObject("SELECT executed_at FROM payments WHERE id = ?", Object.class, payment));
        assertEquals(List.of(), queue(firstAttestantToken));
        assertEquals(0L, count(firstAttestantToken));
        assertEquals(List.of(step2), queue(secondAttestantToken));
        assertEquals(1L, count(secondAttestantToken));
        String secondQueue = getOk("/api/approvals", secondAttestantToken);
        assertEquals(2, (int) JsonPath.read(secondQueue, "$[0].stepNumber"));
        assertEquals(2, (int) JsonPath.read(secondQueue, "$[0].totalSteps"));
        assertEquals(1, (int) JsonPath.read(secondQueue, "$[0].previousApprovals[0].stepNumber"));
        assertEquals("Första Attestant", JsonPath.read(secondQueue, "$[0].previousApprovals[0].approverName"));
        assertEquals(List.of("CREATE_PAYMENT", "APPROVE_STEP"), auditActions(payment));

        assertEquals(204, perform(post("/api/approvals/" + step2 + "/approve"), secondAttestantToken).status());

        assertEquals("completed", paymentStatus(payment));
        assertEquals(0, STARTING_BALANCE.subtract(OVER_TWO_ATTESTANT_THRESHOLD).compareTo(balance()));
        assertEquals(List.of(), queue(secondAttestantToken));
        assertEquals(List.of("CREATE_PAYMENT", "APPROVE_STEP", "APPROVE_PAYMENT"), auditActions(payment));
    }

    // ADR 0017: admin är en administrativ roll och fattar inga beslut om betalningar, så admin kan varken vara en
    // av de två eller ersätta en av dem. Admin ser däremot steget som är på tur, som översikt.
    @Test
    void anAdminCannotDecideOnAnyStep_butSeesWhatWaits() throws Exception {
        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);
        long step1 = stepId(payment, 1);
        long step2 = stepId(payment, 2);

        assertEquals(List.of(step1), queue(adminToken));
        assertEquals(403, perform(post("/api/approvals/" + step1 + "/approve"), adminToken).status());
        assertEquals(403, perform(post("/api/approvals/" + step1 + "/reject"), adminToken).status());
        assertEquals(403, perform(post("/api/approvals/" + step2 + "/approve"), adminToken).status());

        assertEquals("pending_approval", paymentStatus(payment));
        assertEquals("pending", stepStatus(step1));
        assertEquals("pending", stepStatus(step2));
        assertEquals(0, STARTING_BALANCE.compareTo(balance()));
        assertNull(decidedBy(step1));
    }

    @Test
    void anAttestantAssignedToBothSteps_cannotApproveBoth() throws Exception {
        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);
        long step2 = stepId(payment, 2);
        jdbc.update("UPDATE approval_steps SET attestant_id = ? WHERE id = ?", firstAttestant, step2);

        assertEquals(204, perform(post("/api/approvals/" + stepId(payment, 1) + "/approve"), firstAttestantToken).status());

        assertEquals(List.of(), queue(firstAttestantToken));
        assertEquals(403, perform(post("/api/approvals/" + step2 + "/approve"), firstAttestantToken).status());
        assertEquals("pending_approval", paymentStatus(payment));
    }

    // Skaparen kan inte räknas som en av de två, även om ett steg skulle peka på hen (ADR 0016 och ADR 0017).
    // Steget tilldelas om med SQL, eftersom betalningen aldrig tilldelas sin skapare (se nästa test).
    @Test
    void theCreatorOfAPaymentCannotCountAsOneOfTheTwo() throws Exception {
        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);
        long step1 = stepId(payment, 1);
        long step2 = stepId(payment, 2);
        jdbc.update("UPDATE users SET role = 'attestant' WHERE id = ?", initiator);
        jdbc.update("UPDATE approval_steps SET attestant_id = ? WHERE id = ?", initiator, step1);
        String creatorAsAttestant = token(initiator, tenantId, Role.ATTESTANT);

        assertEquals(List.of(), queue(creatorAsAttestant));
        assertEquals(403, perform(post("/api/approvals/" + step1 + "/approve"), creatorAsAttestant).status());
        assertEquals(403, perform(post("/api/approvals/" + step1 + "/reject"), creatorAsAttestant).status());
        assertEquals("pending", stepStatus(step1));
        assertEquals("pending_approval", paymentStatus(payment));
        assertEquals(0, STARTING_BALANCE.compareTo(balance()));

        assertEquals(403, perform(post("/api/approvals/" + step2 + "/approve"), creatorAsAttestant).status());
    }

    // En initiator som just blivit attestant kan ha kvar en initiator-token en stund och skapa en betalning.
    // Hen väljs aldrig som attestant på sin egen betalning, även om hen har det lägsta id:t.
    @Test
    void aCreatorWhoHasJustBecomeAnAttestant_isNeverAssignedToTheirOwnPayment() throws Exception {
        jdbc.update("UPDATE users SET role = 'attestant' WHERE id = ?", initiator);

        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);

        assertEquals(List.of(firstAttestant, secondAttestant), assignedAttestants(payment));
    }

    @Test
    void aCompanyWithTooFewAttestants_getsAClearErrorAndNoPaymentIsCreated() throws Exception {
        jdbc.update("UPDATE users SET role = 'initiator' WHERE id = ?", secondAttestant);

        Response response = perform(createPaymentRequest(OVER_TWO_ATTESTANT_THRESHOLD), initiatorToken);

        assertEquals(409, response.status());
        assertEquals("Det finns inte tillräckligt många attestanter för att ta emot betalningen. Kontakta er administratör.",
                JsonPath.read(response.body(), "$.message"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class));

        // En betalning som bara kräver en attestant går fortfarande igenom
        assertEquals(List.of(firstAttestant), assignedAttestants(createPayment(new BigDecimal("6000.00"), initiatorToken)));
    }

    @Test
    void rejectingTheFirstStep_stopsThePayment_andClosesTheSecondStepWithoutNamingItsAttestant() throws Exception {
        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);
        long step1 = stepId(payment, 1);
        long step2 = stepId(payment, 2);

        // Steg 2 får inte hoppa före steg 1, inte heller för att avvisa
        assertEquals(409, perform(post("/api/approvals/" + step2 + "/reject"), secondAttestantToken).status());
        assertEquals("pending_approval", paymentStatus(payment));

        assertEquals(204, perform(post("/api/approvals/" + step1 + "/reject"), firstAttestantToken).status());

        assertEquals("rejected", paymentStatus(payment));
        assertEquals("rejected", stepStatus(step2));
        assertNull(jdbc.queryForObject("SELECT decided_by FROM approval_steps WHERE id = ?", Long.class, step2));
        assertEquals(0, STARTING_BALANCE.compareTo(balance()));
        assertEquals(List.of(), queue(secondAttestantToken));

        String timeline = getOk("/api/payments/" + payment + "/audit", adminToken);
        List<String> closedStepActors = JsonPath.read(timeline, "$[?(@.stepNumber == 2)].actor");
        assertEquals(List.of("Systemet"), closedStepActors);
    }

    @Test
    void rejectingTheSecondStep_stopsThePaymentAfterTheFirstApproval_withoutMovingMoney() throws Exception {
        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);
        long step1 = stepId(payment, 1);
        long step2 = stepId(payment, 2);

        assertEquals(204, perform(post("/api/approvals/" + step1 + "/approve"), firstAttestantToken).status());
        assertEquals(204, perform(post("/api/approvals/" + step2 + "/reject"), secondAttestantToken).status());

        assertEquals("rejected", paymentStatus(payment));
        assertEquals("approved", stepStatus(step1));
        assertEquals(0, STARTING_BALANCE.compareTo(balance()));
        assertEquals(List.of("CREATE_PAYMENT", "APPROVE_STEP", "REJECT_PAYMENT"), auditActions(payment));
    }

    // Reservationen ska gälla hela vägen: ett första godkännande får inte släppa pengarna till andra betalningar
    @Test
    void thePaymentKeepsItsReservationBetweenTheTwoApprovals() throws Exception {
        long payment = createPayment(new BigDecimal("150000.00"), initiatorToken);

        assertEquals(204, perform(post("/api/approvals/" + stepId(payment, 1) + "/approve"), firstAttestantToken).status());

        assertEquals(400, perform(createPaymentRequest(new BigDecimal("50000.01")), initiatorToken).status());
    }

    @Test
    void attestantsSeeTheFirstApprovalInTheAuditLog_withWhoDidIt() throws Exception {
        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);
        assertEquals(204, perform(post("/api/approvals/" + stepId(payment, 1) + "/approve"), firstAttestantToken).status());

        String timeline = getOk("/api/payments/" + payment + "/audit", secondAttestantToken);

        List<String> actors = JsonPath.read(timeline, "$[?(@.eventType == 'APPROVE_STEP')].actor");
        assertEquals(List.of("Första Attestant"), actors);
    }

    // Före ADR 0017 kunde en admin besluta i en annan attestants ställe. Sådana beslut finns kvar i historiken,
    // och listan ska peka ut den som faktiskt beslutade. Beslutet läggs in med SQL, eftersom det inte längre går
    // att fatta via API:t.
    @Test
    void myPayments_namesWhoActuallyDecided_notWhoTheStepWasAssignedTo() throws Exception {
        long payment = createPayment(OVER_TWO_ATTESTANT_THRESHOLD, initiatorToken);
        jdbc.update("""
                UPDATE approval_steps SET status = 'approved', decided_at = NOW(), decided_by = ?
                WHERE id = ?
                """, admin, stepId(payment, 1));

        String myPayments = getOk("/api/my-payments", initiatorToken);

        List<String> attestants = JsonPath.read(myPayments, "$[0].approvalSteps[*].attestantName");
        assertEquals(List.of("Ada Admin", "Andra Attestant"), attestants);
    }

    @Test
    void anAlreadyPendingPaymentWithOneStep_canStillBeApprovedByOneAttestant() throws Exception {
        // Regeln gäller när betalningen skapas (ADR 0016). En väntande betalning från före ändringen har ett steg.
        Long payment = jdbc.queryForObject("""
                INSERT INTO payments (tenant_id, from_account_id, to_iban, amount, reference, status, created_by)
                VALUES (?, ?, ?, 75000.00, 'Äldre', 'pending_approval', ?)
                RETURNING id
                """, Long.class, tenantId, account, TO_IBAN, admin);
        Long step = jdbc.queryForObject("""
                INSERT INTO approval_steps (payment_id, attestant_id, step_number, status)
                VALUES (?, ?, 1, 'pending')
                RETURNING id
                """, Long.class, payment, firstAttestant);

        assertEquals(204, perform(post("/api/approvals/" + step + "/approve"), firstAttestantToken).status());

        assertEquals("completed", paymentStatus(payment));
    }

    @Test
    void paymentConfig_exposesBothThresholds() throws Exception {
        String config = getOk("/api/payments/config", initiatorToken);

        assertEquals(5000, ((Number) JsonPath.read(config, "$.approvalThreshold")).intValue());
        assertEquals(50000, ((Number) JsonPath.read(config, "$.twoAttestantThreshold")).intValue());
    }

    // --- Data ------------------------------------------------------------------------------------

    private long user(Long tenant, String name, String emailPrefix, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (tenant_id, name, email, password_hash, role)
                VALUES (?, ?, ?, 'unused-test-password-hash', ?)
                RETURNING id
                """, Long.class, tenant, name, emailPrefix + "@dual-approval.test", role);
    }

    private long account(Long tenant) {
        return jdbc.queryForObject("""
                INSERT INTO accounts (tenant_id, account_name, iban, balance, currency)
                VALUES (?, 'Driftkonto', ?, ?, 'SEK')
                RETURNING id
                """, Long.class, tenant, TO_IBAN, STARTING_BALANCE);
    }

    private String token(long userId, Long tenant, Role role) {
        return jwtService.generateToken(new AuthenticatedUserContext(userId, tenant, role));
    }

    // --- HTTP ------------------------------------------------------------------------------------

    private record Response(int status, String body) {}

    private Response perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        var response = mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
        return new Response(response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
    }

    private String getOk(String url, String token) throws Exception {
        Response response = perform(get(url), token);
        assertEquals(200, response.status(), url + ": " + response.body());
        return response.body();
    }

    private MockHttpServletRequestBuilder createPaymentRequest(BigDecimal amount) {
        return post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fromAccountId": %d, "toIban": "%s", "amount": %s, "reference": "Test"}
                        """.formatted(account, TO_IBAN, amount));
    }

    private long createPayment(BigDecimal amount, String token) throws Exception {
        Response response = perform(createPaymentRequest(amount), token);
        assertEquals(201, response.status(), response.body());
        return ((Number) JsonPath.read(response.body(), "$.id")).longValue();
    }

    private List<Long> queue(String token) throws Exception {
        List<Number> ids = JsonPath.read(getOk("/api/approvals", token), "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private long count(String token) throws Exception {
        return ((Number) JsonPath.read(getOk("/api/approvals/count", token), "$.count")).longValue();
    }

    // --- Databasläge -----------------------------------------------------------------------------

    private List<Long> assignedAttestants(long paymentId) {
        return jdbc.queryForList(
                "SELECT attestant_id FROM approval_steps WHERE payment_id = ? ORDER BY step_number",
                Long.class, paymentId);
    }

    private long stepId(long paymentId, int stepNumber) {
        return jdbc.queryForObject(
                "SELECT id FROM approval_steps WHERE payment_id = ? AND step_number = ?",
                Long.class, paymentId, stepNumber);
    }

    private String stepStatus(long stepId) {
        return jdbc.queryForObject("SELECT status FROM approval_steps WHERE id = ?", String.class, stepId);
    }

    private Long decidedBy(long stepId) {
        return jdbc.queryForObject("SELECT decided_by FROM approval_steps WHERE id = ?", Long.class, stepId);
    }

    private String paymentStatus(long paymentId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId);
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    private List<String> auditActions(long paymentId) {
        return jdbc.queryForList(
                "SELECT action FROM audit_entries WHERE entity_id = ? AND entity_type = 'PAYMENT' ORDER BY id",
                String.class, paymentId);
    }
}
