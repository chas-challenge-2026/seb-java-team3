package se.comerit.seb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import se.comerit.seb.domain.Role;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Riktig HTTP-kedja med JWT, Flyway och Postgres. Ingen servicelogik eller databas mockas.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@Testcontainers
class PaymentCreationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:12");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired JwtService jwt;
    long tenant;
    long creator;
    long account;
    long otherAccount;
    String token;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE refresh_tokens, audit_entries, approval_steps, payments, accounts, users, tenants RESTART IDENTITY CASCADE");
        tenant = company("Test AB");
        creator = user(tenant, "initiator@test.se");
        token = token(creator, tenant);
        account = account(tenant);
        otherAccount = account(tenant);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 101, 140})
    void referenceFitsTheMigratedColumn(int length) throws Exception {
        Response response = create(token, account, "100.00", "R".repeat(length), "reference");
        assertEquals(201, response.status(), response.body());
        assertEquals("R".repeat(length), jdbc.queryForObject("SELECT reference FROM payments", String.class));
    }

    @Test
    void tooLongReferenceIsRejectedBeforeAnythingIsWritten() throws Exception {
        assertEquals(400, create(token, account, "100", "R".repeat(141), "reference").status());
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "5000.001", "10000000000000"})
    void invalidMoneyNeverReachesTheDecimalColumn(String amount) throws Exception {
        assertEquals(400, create(token, account, amount, "Test", "amount").status());
        assertUnchanged();
    }

    @Test
    void trailingZerosDoNotLoseMoney() throws Exception {
        assertEquals(201, create(token, account, "100.000", "Test", "amount").status());
        assertEquals(new BigDecimal("100.00"), jdbc.queryForObject("SELECT amount FROM payments", BigDecimal.class));
        assertEquals(new BigDecimal("9900.00"), balance(account));
    }

    @Test
    void retryReturnsTheSamePaymentWithoutAnotherDebitOrAudit() throws Exception {
        Response first = create(token, account, "100", "Test", "retry");
        Response again = create(token, account, "100.00", "Test", "retry");
        assertSamePayment(first, again);
        assertEquals(1, count("payments"));
        assertEquals(1, count("audit_entries"));
        assertEquals(new BigDecimal("9900.00"), balance(account));
    }

    @Test
    void retryOfAPendingPaymentDoesNotReserveAgainOrCreateAnotherApprovalStep() throws Exception {
        jdbc.update("INSERT INTO users(tenant_id, name, email, password_hash, role) VALUES (?, 'Attestant', 'attestant@test.se', 'unused-test-hash', 'attestant')", tenant);
        Response first = create(token, account, "6000", "Test", "pending-retry");
        Response again = create(token, account, "6000.00", "Test", "pending-retry");
        assertSamePayment(first, again);
        assertEquals("PENDING_APPROVAL", JsonPath.read(again.body(), "$.status"));
        assertEquals(1, count("payments"));
        assertEquals(1, count("approval_steps"));
        assertEquals(1, count("audit_entries"));
        assertEquals(new BigDecimal("10000.00"), balance(account));
    }

    @Test
    void changingTheAmountReferenceOrAccountWithTheSameKeyIsAConflict() throws Exception {
        assertEquals(201, create(token, account, "100", "Test", "retry").status());
        assertEquals(409, create(token, otherAccount, "100", "Test", "retry").status());
        assertEquals(409, create(token, account, "101", "Test", "retry").status());
        assertEquals(409, create(token, account, "100", "Changed", "retry").status());
        assertEquals(1, count("payments"));
        assertEquals(1, count("audit_entries"));
        assertEquals(new BigDecimal("10000.00"), balance(otherAccount));
    }

    @Test
    void keysAreScopedToTheUserAndCompany() throws Exception {
        Response first = create(token, account, "100", "Test", "shared");
        long colleague = user(tenant, "colleague@test.se");
        Response second = create(token(colleague, tenant), account, "100", "Test", "shared");
        long otherTenant = company("Other AB");
        long otherUser = user(otherTenant, "other@test.se");
        Response third = create(token(otherUser, otherTenant), account(otherTenant), "100", "Test", "shared");
        for (Response response : List.of(first, second, third)) assertEquals(201, response.status(), response.body());
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.id(), third.id());
        assertEquals(3, count("payments"));
    }

    @Test
    void rollbackDoesNotConsumeTheKeyOrLeaveADebit() throws Exception {
        jdbc.execute("""
                CREATE FUNCTION fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'test rollback'; END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_audit BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION fail_audit()");
        try {
            assertEquals(500, create(token, account, "100", "Test", "rollback").status());
            assertUnchanged();
        } finally {
            jdbc.execute("DROP TRIGGER fail_audit ON audit_entries");
            jdbc.execute("DROP FUNCTION fail_audit()");
        }
        assertEquals(201, create(token, account, "100", "Test", "rollback").status());
        assertEquals(1, count("payments"));
        assertEquals(new BigDecimal("9900.00"), balance(account));
    }

    @Test
    void concurrentRetriesCreateExactlyOnePayment() throws Exception {
        List<Response> responses = race(account, account);
        assertSamePayment(responses.get(0), responses.get(1));
        assertEquals(1, count("payments"));
        assertEquals(1, count("audit_entries"));
        assertEquals(new BigDecimal("9900.00"), balance(account));
    }

    @Test
    void concurrentReuseWithAnotherAccountIsAConflictRatherThanADuplicate() throws Exception {
        List<Response> responses = race(account, otherAccount);
        assertEquals(List.of(201, 409), responses.stream().map(Response::status).sorted().toList());
        assertEquals(1, count("payments"));
        assertEquals(1, count("audit_entries"));
        assertEquals(new BigDecimal("19900.00"), balance(account).add(balance(otherAccount)));
    }

    // En tredje anslutning håller skaparraden. Båda HTTP-anropen måste vänta på ett riktigt
    // databaslås innan spärren släpps, så testet prövar konkurrens även på en långsam CI-runner.
    private List<Response> race(long firstAccount, long secondAccount) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try (Connection gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            try (var lock = gate.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, creator);
                lock.setQueryTimeout(10);
                try (var rows = lock.executeQuery()) { assertTrue(rows.next()); }
            }
            var first = workers.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return create(token, firstAccount, "100", "Test", "race"); });
            var second = workers.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return create(token, secondAccount, "100", "Test", "race"); });
            start.countDown();
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                    while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock' AND query ILIKE '%users%'", Integer.class) < 2) {
                        Thread.sleep(20);
                    }
                });
            } finally {
                gate.rollback();
            }
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private Response create(String bearer, long fromAccount, String amount, String reference, String key) throws Exception {
        var request = post("/api/payments").header("Authorization", "Bearer " + bearer)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("fromAccountId", fromAccount,
                        "toIban", "DE89370400440532013000", "amount", new BigDecimal(amount), "reference", reference)));
        var result = mvc.perform(request).andReturn().getResponse();
        return new Response(result.getStatus(), result.getContentAsString(StandardCharsets.UTF_8));
    }

    private record Response(int status, String body) {
        long id() { return ((Number) JsonPath.read(body, "$.id")).longValue(); }
    }

    private void assertSamePayment(Response first, Response second) {
        assertEquals(201, first.status(), first.body());
        assertEquals(201, second.status(), second.body());
        assertEquals(first.id(), second.id());
    }

    private long company(String name) { return jdbc.queryForObject("INSERT INTO tenants(name) VALUES (?) RETURNING id", Long.class, name); }
    private long user(long company, String email) { return jdbc.queryForObject("INSERT INTO users(tenant_id, name, email, password_hash, role) VALUES (?, 'Test', ?, 'unused-test-hash', 'initiator') RETURNING id", Long.class, company, email); }
    private long account(long company) { return jdbc.queryForObject("INSERT INTO accounts(tenant_id, account_name, balance) VALUES (?, 'Testkonto', 10000) RETURNING id", Long.class, company); }
    private String token(long user, long company) { return jwt.generateToken(new AuthenticatedUserContext(user, company, Role.INITIATOR)); }
    private BigDecimal balance(long id) { return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, id); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private void assertUnchanged() {
        assertEquals(0, count("payments"));
        assertEquals(0, count("audit_entries"));
        assertEquals(new BigDecimal("10000.00"), balance(account));
    }
}
