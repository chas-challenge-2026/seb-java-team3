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
import org.springframework.test.web.servlet.RequestBuilder;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// R-04: företag A får aldrig se eller ändra företag B:s data, på någon endpoint som bär företagsdata.
// Hela kedjan körs: riktig JWT -> filter -> @PreAuthorize -> controller -> service -> repository ->
// Postgres 12 (samma motor som i drift, Flyway-migreringarna körs). Inget är mockat.
//
// Två företag med samma uppsättning: initiatör, attestant, admin, ett konto, en väntande och en
// genomförd betalning, och auditrader. All text hos ett företag innehåller dess markör (BOLAG_A,
// BOLAG_B), så att ett svar kan sökas igenom efter läckage även i fält som testet inte känner till.
//
// Dessutom finns rader som korsar företagsgränsen, sådana som bara uppstår av felaktig data (till
// exempel betalningar från tiden före kontrollerna i PaymentService). De visar att det är
// tenant-villkoret som skyddar, inte bara att användar-id:n råkar skilja sig åt:
//  - en betalning hos B som skapats av A:s initiatör och väntar på A:s attestant
//  - en auditrad hos A som pekar på B:s väntande betalning
//  - en auditrad hos B som pekar på A:s väntande betalning
//  - en auditrad hos A med en användare från B
//
// Varje test har en kontroll som visar att det egna företagets data faktiskt kommer med, så att
// "B:s data syns inte" inte kan bli grönt bara för att svaret är tomt.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@Testcontainers
class TenantIsolationIntegrationTest {

    private static final String TO_IBAN_A = "SE8550000000054910000003";
    private static final String TO_IBAN_B = "SE7280000810340009783242";
    // Klarar MOD97, så att en nekad betalning nedan beror på kontot och inte på mottagaren
    private static final String VALID_IBAN = "SE4550000000058398257466";
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("100000.00");
    private static final long MISSING_ID = 999_999L;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:12");

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

    private Company a;
    private Company b;

    // B:s betalning som skapats av A:s initiatör och väntar på A:s attestant
    private long crossPayment;
    private long crossStep;
    // A:s auditrad som pekar på B:s betalning, och B:s auditrad som pekar på A:s
    private long auditOfAPointingAtB;
    private long auditOfBPointingAtA;
    // A:s auditrad om A:s betalning, men med B:s admin som användare
    private long auditOfAByUserOfB;

    @BeforeEach
    void seedTwoCompanies() {
        // Containern hör bara till den här testklassen. Allt, även dev-seeden från V5, töms före
        // varje test, så att mängderna nedan kan jämföras exakt.
        jdbc.execute("""
                TRUNCATE TABLE refresh_tokens, audit_entries, approval_steps, payments, accounts, users, tenants
                RESTART IDENTITY CASCADE
                """);

        a = company("BOLAG_A", "a", TO_IBAN_A, "SE4550000000058398257466");
        b = company("BOLAG_B", "b", TO_IBAN_B, "SE4550000000058398257477");

        crossPayment = payment(b.tenantId, b.account, a.initiator, "BOLAG_B korsande faktura", "pending_approval", TO_IBAN_B);
        crossStep = step(crossPayment, a.attestant);
        auditOfAPointingAtB = audit(a.tenantId, a.admin, "CREATE_PAYMENT", b.pendingPayment, "BOLAG_A pekar på annan betalning");
        auditOfBPointingAtA = audit(b.tenantId, b.admin, "CREATE_PAYMENT", a.pendingPayment, "BOLAG_B pekar på annan betalning");
        auditOfAByUserOfB = audit(a.tenantId, b.admin, "CREATE_PAYMENT", a.pendingPayment, "BOLAG_A rad med användare från annat företag");
    }

    @Test
    void accounts_showOnlyTheOwnCompanysAccounts() throws Exception {
        String forA = getOk("/api/accounts", a.initiatorToken);
        String forB = getOk("/api/accounts", b.initiatorToken);

        assertEquals(Set.of(a.account), ids(forA, "$[*].id"));
        assertEquals(Set.of(b.account), ids(forB, "$[*].id"));
        assertNoTraceOf(b, forA);
        assertNoTraceOf(a, forB);
    }

    @Test
    void auditLog_forAdmin_showsEveryEntryOfTheOwnCompanyAndNothingElse() throws Exception {
        String forA = getOk("/api/audit", a.adminToken);
        String forB = getOk("/api/audit", b.adminToken);

        assertEquals(a.auditPlus(auditOfAPointingAtB, auditOfAByUserOfB), ids(forA, "$[*].id"));
        assertEquals(b.auditPlus(auditOfBPointingAtA), ids(forB, "$[*].id"));
        assertNoTraceOf(b, forA);
        assertNoTraceOf(a, forB);
    }

    @Test
    void auditLog_forAttestant_showsOnlyPaymentEventsOfTheOwnCompany() throws Exception {
        String forA = getOk("/api/audit", a.attestantToken);

        // Inloggningen är bara för admin; betalningshändelserna är kvar
        assertEquals(a.paymentAuditPlus(auditOfAPointingAtB, auditOfAByUserOfB), ids(forA, "$[*].id"));
        assertNoTraceOf(b, forA);
    }

    @Test
    void auditLog_entryPointingAtAnotherCompanysPayment_isNotEnrichedWithThatPayment() throws Exception {
        String forA = getOk("/api/audit", a.adminToken);

        Map<String, Object> entry = entryWithId(forA, auditOfAPointingAtB);
        assertNull(entry.get("status"));
        assertNull(entry.get("reference"));
        assertNull(entry.get("amount"));
        assertNull(entry.get("fromAccountName"));
        assertNull(entry.get("balanceBeforePayment"));

        // Kontroll: A:s egen betalning berikas som vanligt
        Map<String, Object> own = entryWithId(forA, a.createPendingAudit);
        assertEquals("BOLAG_A väntande faktura", own.get("reference"));
        assertEquals("BOLAG_A Driftkonto", own.get("fromAccountName"));
    }

    @Test
    void auditLogAndTimeline_neverNameAnotherCompanysUser() throws Exception {
        String auditLog = getOk("/api/audit", a.adminToken);
        String timeline = getOk("/api/payments/" + a.pendingPayment + "/audit", a.adminToken);

        // Raden visas, men användaren från B är okänd inom A
        assertEquals("Systemet", entryWithId(auditLog, auditOfAByUserOfB).get("userName"));
        List<String> actors = JsonPath.read(timeline, "$[?(@.description == 'BOLAG_A rad med användare från annat företag')].actor");
        assertEquals(List.of("Systemet"), actors);

        // Kontroll: A:s egna användare namnges som vanligt
        assertEquals("BOLAG_A Initiatör", entryWithId(auditLog, a.createPendingAudit).get("userName"));
        assertNoTraceOf(b, auditLog);
        assertNoTraceOf(b, timeline);
    }

    @Test
    void paymentTimeline_ofAnotherCompanysPayment_looksExactlyLikeAMissingPayment() throws Exception {
        for (String token : List.of(a.adminToken, a.attestantToken)) {
            String own = getOk("/api/payments/" + a.pendingPayment + "/audit", token);
            assertFalse(ids(own, "$[*].order").isEmpty(), "kontroll: A:s egen tidslinje ska ha händelser");

            Response other = perform(get("/api/payments/" + b.pendingPayment + "/audit"), token);
            Response missing = perform(get("/api/payments/" + MISSING_ID + "/audit"), token);

            assertEquals(400, other.status());
            assertEquals(missing.status(), other.status());
            assertNoTraceOf(b, other.body());
        }
    }

    @Test
    void paymentTimeline_ofOwnPayment_leavesOutAnotherCompanysEntriesAboutTheSameId() throws Exception {
        // B har en auditrad med A:s betalnings-id; den får inte dyka upp i A:s tidslinje
        String forA = getOk("/api/payments/" + a.pendingPayment + "/audit", a.adminToken);

        assertTrue(forA.contains("BOLAG_A"), "kontroll: A:s egna händelser ska finnas med");
        assertNoTraceOf(b, forA);
    }

    @Test
    void myPayments_showOnlyTheOwnCompanysPayments_evenWhenAnotherCompanysPaymentHasTheSameCreator() throws Exception {
        String forA = getOk("/api/my-payments", a.initiatorToken);
        String forB = getOk("/api/my-payments", b.initiatorToken);

        // Den korsande betalningen hos B har A:s initiatör som skapare men hör till B
        assertEquals(Set.of(a.pendingPayment, a.completedPayment), ids(forA, "$[*].paymentId"));
        assertEquals(Set.of(b.pendingPayment, b.completedPayment), ids(forB, "$[*].paymentId"));
        assertNoTraceOf(b, forA);
        assertNoTraceOf(a, forB);
    }

    @Test
    void approvalQueue_showsOnlyTheOwnCompanysSteps_evenWhenAnotherCompanysStepIsAssignedToTheUser() throws Exception {
        String forAttestantA = getOk("/api/approvals", a.attestantToken);
        String forAdminA = getOk("/api/approvals", a.adminToken);

        // Det korsande steget hos B är tilldelat A:s attestant men hör till B
        assertEquals(Set.of(a.pendingStep), ids(forAttestantA, "$[*].id"));
        assertEquals(Set.of(a.pendingStep), ids(forAdminA, "$[*].id"));
        assertNoTraceOf(b, forAttestantA);
        assertNoTraceOf(b, forAdminA);

        assertEquals(1L, count(a.attestantToken));
        assertEquals(1L, count(a.adminToken));
    }

    @Test
    void approvalQueue_neverNamesAnotherCompanysInitiator() throws Exception {
        String forAdminB = getOk("/api/approvals", b.adminToken);

        assertEquals(Set.of(b.pendingStep, crossStep), ids(forAdminB, "$[*].id"));
        assertEquals("BOLAG_B Initiatör", entryWithId(forAdminB, b.pendingStep).get("initiatorName"));
        assertNull(entryWithId(forAdminB, crossStep).get("initiatorName"));
        assertNoTraceOf(a, forAdminB);
        assertEquals(2L, count(b.adminToken));
    }

    @Test
    void approveAndReject_ofAnotherCompanysStep_lookExactlyLikeAMissingStepAndChangeNothing() throws Exception {
        // B:s väntande steg, B:s steg som är tilldelat A:s attestant, och B:s redan godkända steg: ingen
        // av dem får gå att skilja från ett steg som inte finns, oavsett status
        for (long stepOfB : List.of(b.pendingStep, crossStep, b.decidedStep)) {
            for (String token : List.of(a.adminToken, a.attestantToken)) {
                for (String action : List.of("approve", "reject")) {
                    assertLooksLikeMissingStep(stepOfB, action, token);
                }
            }
        }

        assertUntouched(b.pendingPayment, b.pendingStep, b.account);
        assertUntouched(crossPayment, crossStep, b.account);
        assertEquals("completed", paymentStatus(b.completedPayment));
        assertEquals("approved", jdbc.queryForObject(
                "SELECT status FROM approval_steps WHERE id = ?", String.class, b.decidedStep));

        // Kontroll: A:s attestant kan godkänna sitt eget steg
        assertEquals(204, perform(post("/api/approvals/" + a.pendingStep + "/approve"), a.attestantToken).status());
        assertEquals("completed", paymentStatus(a.pendingPayment));
        assertUntouched(b.pendingPayment, b.pendingStep, b.account);
    }

    @Test
    void createPayment_fromAnotherCompanysAccount_isRefusedAndChangesNothing() throws Exception {
        long paymentsBefore = paymentCount(b.tenantId);

        // Även med B:s tenantId i body: tenant tas från JWT, aldrig från requesten
        Response response = perform(createPayment(b.tenantId, b.account), a.initiatorToken);

        // Samma meddelande som för ett konto som inte finns, och det är kontot som stoppar betalningen
        assertEquals(400, response.status());
        assertEquals("Från-kontot finns inte eller tillhör inte ditt företag",
                JsonPath.read(response.body(), "$.message"));
        assertNoTraceOf(b, response.body());
        assertEquals(paymentsBefore, paymentCount(b.tenantId));
        assertEquals(0, STARTING_BALANCE.compareTo(balance(b.account)));
    }

    @Test
    void createPayment_withAnotherCompanysTenantIdInTheBody_isStillCreatedForTheOwnCompany() throws Exception {
        long paymentsOfBBefore = paymentCount(b.tenantId);

        Response response = perform(createPayment(b.tenantId, a.account), a.initiatorToken);

        assertEquals(201, response.status());
        long created = ((Number) JsonPath.read(response.body(), "$.id")).longValue();
        assertEquals(a.tenantId, jdbc.queryForObject("SELECT tenant_id FROM payments WHERE id = ?", Long.class, created));
        assertEquals(paymentsOfBBefore, paymentCount(b.tenantId));
    }

    // --- Fixtur -----------------------------------------------------------------------------------

    private Company company(String marker, String emailPrefix, String toIban, String accountIban) {
        Company company = new Company();
        company.marker = marker;
        company.toIban = toIban;
        company.accountIban = accountIban;
        company.tenantId = jdbc.queryForObject(
                "INSERT INTO tenants (name) VALUES (?) RETURNING id", Long.class, marker + " AB");
        company.initiator = user(company.tenantId, marker + " Initiatör", emailPrefix + "-initiator", "initiator");
        company.attestant = user(company.tenantId, marker + " Attestant", emailPrefix + "-attestant", "attestant");
        company.admin = user(company.tenantId, marker + " Admin", emailPrefix + "-admin", "admin");
        company.account = jdbc.queryForObject("""
                INSERT INTO accounts (tenant_id, account_name, iban, balance, currency)
                VALUES (?, ?, ?, ?, 'SEK')
                RETURNING id
                """, Long.class, company.tenantId, marker + " Driftkonto", accountIban, STARTING_BALANCE);

        company.pendingPayment = payment(company.tenantId, company.account, company.initiator,
                marker + " väntande faktura", "pending_approval", toIban);
        company.pendingStep = step(company.pendingPayment, company.attestant);
        company.completedPayment = payment(company.tenantId, company.account, company.initiator,
                marker + " genomförd faktura", "completed", toIban);
        company.decidedStep = step(company.completedPayment, company.attestant, "approved");

        company.createPendingAudit = audit(company.tenantId, company.initiator, "CREATE_PAYMENT",
                company.pendingPayment, marker + " skapade väntande betalning");
        long createCompleted = audit(company.tenantId, company.initiator, "CREATE_PAYMENT",
                company.completedPayment, marker + " skapade genomförd betalning");
        long approveCompleted = audit(company.tenantId, company.attestant, "APPROVE_PAYMENT",
                company.completedPayment, marker + " godkände betalning");
        company.paymentAudit = Set.of(company.createPendingAudit, createCompleted, approveCompleted);
        // En händelse som bara admin ser
        company.loginAudit = jdbc.queryForObject("""
                INSERT INTO audit_entries (tenant_id, user_id, action, entity_type, entity_id, description)
                VALUES (?, ?, 'LOGIN', 'USER', ?, ?)
                RETURNING id
                """, Long.class, company.tenantId, company.admin, company.admin, marker + " loggade in");

        company.initiatorToken = token(company.initiator, company.tenantId, Role.INITIATOR);
        company.attestantToken = token(company.attestant, company.tenantId, Role.ATTESTANT);
        company.adminToken = token(company.admin, company.tenantId, Role.ADMIN);
        return company;
    }

    private long user(Long tenantId, String name, String emailPrefix, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (tenant_id, name, email, password_hash, role)
                VALUES (?, ?, ?, 'unused-test-password-hash', ?)
                RETURNING id
                """, Long.class, tenantId, name, emailPrefix + "@tenant-isolation.test", role);
    }

    private long payment(Long tenantId, long accountId, long createdBy, String reference, String status, String toIban) {
        return jdbc.queryForObject("""
                INSERT INTO payments (tenant_id, from_account_id, to_iban, amount, currency, reference, status, created_by)
                VALUES (?, ?, ?, 20000.00, 'SEK', ?, ?, ?)
                RETURNING id
                """, Long.class, tenantId, accountId, toIban, reference, status, createdBy);
    }

    private long step(long paymentId, long attestantId) {
        return step(paymentId, attestantId, "pending");
    }

    private long step(long paymentId, long attestantId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO approval_steps (payment_id, attestant_id, step_number, status)
                VALUES (?, ?, 1, ?)
                RETURNING id
                """, Long.class, paymentId, attestantId, status);
    }

    private long audit(Long tenantId, long userId, String action, long paymentId, String description) {
        return jdbc.queryForObject("""
                INSERT INTO audit_entries (tenant_id, user_id, action, entity_type, entity_id, description)
                VALUES (?, ?, ?, 'PAYMENT', ?, ?)
                RETURNING id
                """, Long.class, tenantId, userId, action, paymentId, description);
    }

    private String token(long userId, Long tenantId, Role role) {
        return jwtService.generateToken(new AuthenticatedUserContext(userId, tenantId, role));
    }

    // --- HTTP ------------------------------------------------------------------------------------

    private record Response(int status, String body) {}

    private Response perform(MockHttpServletRequestBuilder request,
                             String token) throws Exception {
        RequestBuilder withToken = request.header("Authorization", "Bearer " + token);
        var response = mockMvc.perform(withToken).andReturn().getResponse();
        return new Response(response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
    }

    private String getOk(String url, String token) throws Exception {
        Response response = perform(get(url), token);
        assertEquals(200, response.status(), url + ": " + response.body());
        return response.body();
    }

    private long count(String token) throws Exception {
        return ((Number) JsonPath.read(getOk("/api/approvals/count", token), "$.count")).longValue();
    }

    private static MockHttpServletRequestBuilder createPayment(
            Long tenantIdInBody, long fromAccountId) {
        return post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tenantId": %d, "fromAccountId": %d, "toIban": "%s", "amount": 100.00, "reference": "Test"}
                        """.formatted(tenantIdInBody, fromAccountId, VALID_IBAN));
    }

    private static Set<Long> ids(String json, String path) {
        List<Number> values = JsonPath.read(json, path);
        return values.stream().map(Number::longValue).collect(Collectors.toSet());
    }

    private static Map<String, Object> entryWithId(String json, long id) {
        List<Map<String, Object>> matches = JsonPath.read(json, "$[?(@.id == " + id + ")]");
        assertEquals(1, matches.size(), "rad " + id + " ska finnas exakt en gång");
        return matches.get(0);
    }

    // Inget från det andra företaget: varken text (markören finns i namn, referenser, kontonamn och
    // beskrivningar) eller IBAN.
    private static void assertNoTraceOf(Company other, String body) {
        assertFalse(body.contains(other.marker), () -> "läcker " + other.marker + ": " + body);
        assertFalse(body.contains(other.accountIban), () -> "läcker " + other.marker + "s konto-IBAN: " + body);
        assertFalse(body.contains(other.toIban), () -> "läcker " + other.marker + "s mottagar-IBAN: " + body);
    }

    // Samma status och samma text (bortsett från id:t) som för ett steg som inte finns
    private void assertLooksLikeMissingStep(long stepId, String action, String token) throws Exception {
        Response other = perform(post("/api/approvals/" + stepId + "/" + action), token);
        Response missing = perform(post("/api/approvals/" + MISSING_ID + "/" + action), token);

        assertEquals(400, other.status(), () -> action + " " + stepId + ": " + other.body());
        assertEquals(missing.status(), other.status());
        String missingMessage = JsonPath.read(missing.body(), "$.message");
        assertEquals(missingMessage.replace(String.valueOf(MISSING_ID), String.valueOf(stepId)),
                JsonPath.read(other.body(), "$.message"));
    }

    // --- Databasläge ------------------------------------------------------------------------------

    private void assertUntouched(long paymentId, long stepId, long accountId) {
        assertEquals("pending_approval", paymentStatus(paymentId));
        assertEquals("pending", jdbc.queryForObject(
                "SELECT status FROM approval_steps WHERE id = ?", String.class, stepId));
        assertNull(jdbc.queryForObject(
                "SELECT decided_at FROM approval_steps WHERE id = ?", Object.class, stepId));
        assertEquals(0, STARTING_BALANCE.compareTo(balance(accountId)));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_entries
                WHERE entity_type = 'PAYMENT' AND entity_id = ? AND action IN ('APPROVE_PAYMENT', 'REJECT_PAYMENT')
                """, Long.class, paymentId));
    }

    private String paymentStatus(long paymentId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId);
    }

    private BigDecimal balance(long accountId) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private long paymentCount(Long tenantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE tenant_id = ?", Long.class, tenantId);
    }

    private static final class Company {
        String marker;
        String toIban;
        String accountIban;
        Long tenantId;
        long initiator;
        long attestant;
        long admin;
        long account;
        long pendingPayment;
        long pendingStep;
        long decidedStep;
        long completedPayment;
        long createPendingAudit;
        long loginAudit;
        Set<Long> paymentAudit;
        String initiatorToken;
        String attestantToken;
        String adminToken;

        Set<Long> paymentAuditPlus(Long... extra) {
            return Stream.concat(paymentAudit.stream(), Stream.of(extra)).collect(Collectors.toSet());
        }

        Set<Long> auditPlus(Long... extra) {
            return Stream.concat(paymentAuditPlus(extra).stream(), Stream.of(loginAudit)).collect(Collectors.toSet());
        }
    }
}
