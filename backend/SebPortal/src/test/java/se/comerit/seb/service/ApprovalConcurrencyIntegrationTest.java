package se.comerit.seb.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import se.comerit.seb.domain.Role;
import se.comerit.seb.exception.ApprovalNotPendingException;
import se.comerit.seb.security.AuthenticatedUserContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "audit.log-path="})
@Testcontainers
class ApprovalConcurrencyIntegrationTest {

    private static final int REPETITIONS = 20;
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal FIRST_AMOUNT = new BigDecimal("200.00");
    private static final BigDecimal SECOND_AMOUNT = new BigDecimal("300.00");
    private static final String REJECTION_COMMENT = "Issue 235 rejected concurrently";

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
    private ApprovalService approvalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private Long tenantId;
    private Long accountId;
    private AuthenticatedUserContext initiator;
    private AuthenticatedUserContext attestant;
    private AuthenticatedUserContext secondAttestant;

    @BeforeEach
    void createFixture() {
        // No test transaction: the fixture is committed before either worker starts.
        tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name) VALUES (?) RETURNING id",
                Long.class, "Issue 235 concurrency tenant");
        initiator = createUser(Role.INITIATOR, "initiator");
        attestant = createUser(Role.ATTESTANT, "attestant");
        secondAttestant = createUser(Role.ATTESTANT, "attestant2");
        accountId = jdbcTemplate.queryForObject("""
                INSERT INTO accounts (tenant_id, account_name, iban, balance, currency)
                VALUES (?, ?, ?, ?, 'SEK') RETURNING id
                """, Long.class, tenantId, "Issue 235 account", "SE4550000000058398257466",
                STARTING_BALANCE);
    }

    @AfterEach
    void removeFixture() {
        // Only this repetition's tenant is touched; the Flyway seed is left intact.
        if (tenantId != null) {
            jdbcTemplate.update("DELETE FROM audit_entries WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("""
                    DELETE FROM approval_steps WHERE payment_id IN
                        (SELECT id FROM payments WHERE tenant_id = ?)
                    """, tenantId);
            jdbcTemplate.update("DELETE FROM payments WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM accounts WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    // Samma attestant godkänner samma steg två gånger samtidigt (dubbelklick, två flikar).
    @RepeatedTest(REPETITIONS)
    void sameStepApprovedTwiceByTheAssignedAttestant_debitsOnlyOnce() throws Exception {
        PaymentFixture payment = createPayment(FIRST_AMOUNT, attestant);

        List<Outcome> outcomes = race("SELECT id FROM payments WHERE id = ? FOR UPDATE", payment.id(),
                approve(payment, attestant), approve(payment, attestant));

        Outcome winner = assertSingleDecision(outcomes);
        assertEquals(STARTING_BALANCE.subtract(FIRST_AMOUNT), readBalance());
        PaymentState state = assertCompleted(payment, winner.actorId());
        assertEquals(STARTING_BALANCE, state.balanceBefore());
    }

    // Den tilldelade attestanten godkänner och avvisar samma betalning samtidigt (två flikar).
    @RepeatedTest(REPETITIONS)
    void approveAndRejectSamePayment_onlyOneConsistentDecision() throws Exception {
        PaymentFixture payment = createPayment(FIRST_AMOUNT, attestant);

        List<Outcome> outcomes = race("SELECT id FROM payments WHERE id = ? FOR UPDATE", payment.id(),
                approve(payment, attestant),
                new Decision(attestant, "reject",
                        () -> approvalService.rejectForUser(payment.stepId(), attestant, REJECTION_COMMENT)));

        Outcome winner = assertSingleDecision(outcomes);
        if (winner.kind().equals("approve")) {
            assertEquals(STARTING_BALANCE.subtract(FIRST_AMOUNT), readBalance());
            PaymentState state = assertCompleted(payment, attestant.userId());
            assertEquals(STARTING_BALANCE, state.balanceBefore());
        } else {
            assertEquals("reject", winner.kind());
            assertEquals(STARTING_BALANCE, readBalance());
            PaymentState state = readPayment(payment);
            assertEquals("rejected", state.paymentStatus());
            assertEquals("rejected", state.stepStatus());
            assertNull(state.executedAt());
            assertNotNull(state.decidedAt());
            assertEquals(REJECTION_COMMENT, state.comment());
            assertNull(state.balanceBefore());
            assertNull(state.balanceAfter());
            assertAudit(payment, "REJECT_PAYMENT", attestant.userId());
        }
    }

    @RepeatedTest(REPETITIONS)
    void differentPaymentsFromSameAccount_preserveBothDebits() throws Exception {
        PaymentFixture first = createPayment(FIRST_AMOUNT, attestant);
        PaymentFixture second = createPayment(SECOND_AMOUNT, secondAttestant);

        List<Outcome> outcomes = race("SELECT id FROM accounts WHERE id = ? FOR UPDATE", accountId,
                approve(first, attestant), approve(second, secondAttestant));

        outcomes.forEach(outcome -> assertNull(outcome.failure(), () -> "Decision failed: " + outcome.failure()));
        BigDecimal finalBalance = STARTING_BALANCE.subtract(FIRST_AMOUNT).subtract(SECOND_AMOUNT);
        assertEquals(finalBalance, readBalance());
        PaymentState firstState = assertCompleted(first, attestant.userId());
        PaymentState secondState = assertCompleted(second, secondAttestant.userId());
        // Either payment may win the account lock, but the receipts must form one debit chain.
        PaymentState earlier = firstState.balanceBefore().equals(STARTING_BALANCE) ? firstState : secondState;
        PaymentState later = earlier == firstState ? secondState : firstState;
        assertEquals(STARTING_BALANCE, earlier.balanceBefore());
        assertEquals(earlier.balanceAfter(), later.balanceBefore());
        assertEquals(finalBalance, later.balanceAfter());
    }

    private AuthenticatedUserContext createUser(Role role, String label) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO users (tenant_id, name, email, password_hash, role)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, tenantId, "Issue 235 " + label,
                "issue235-" + label + "-" + tenantId + "@seb.test",
                "unused-test-password-hash", role.name().toLowerCase(Locale.ROOT));
        return new AuthenticatedUserContext(id, tenantId, role);
    }

    // Skapad av initiatorn och tilldelad den attestant som ska fatta beslutet.
    private PaymentFixture createPayment(BigDecimal amount, AuthenticatedUserContext assignedAttestant) {
        Long paymentId = jdbcTemplate.queryForObject("""
                INSERT INTO payments
                    (tenant_id, from_account_id, to_iban, amount, currency, reference, status, created_by)
                VALUES (?, ?, ?, ?, 'SEK', ?, 'pending_approval', ?) RETURNING id
                """, Long.class, tenantId, accountId, "SE8550000000054910000003", amount,
                "Issue 235 concurrency test", initiator.userId());
        Long stepId = jdbcTemplate.queryForObject("""
                INSERT INTO approval_steps (payment_id, attestant_id, step_number, status)
                VALUES (?, ?, 1, 'pending') RETURNING id
                """, Long.class, paymentId, assignedAttestant.userId());
        return new PaymentFixture(paymentId, stepId, amount);
    }

    private Decision approve(PaymentFixture payment, AuthenticatedUserContext user) {
        return new Decision(user, "approve", () -> approvalService.approveForUser(payment.stepId(), user));
    }

    private List<Outcome> race(String gateSql, Long gateId, Decision first, Decision second) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<DatabaseSession> sessions = new ConcurrentLinkedQueue<>();
        List<Future<Outcome>> futures = new ArrayList<>();
        try {
            // The gate forces actual database contention, even on a single-core CI runner.
            // It is a separate JDBC transaction, never a preloaded JPA entity in a worker.
            // With a missing service lock, both workers can read stale state before their
            // UPDATE waits here instead. Releasing the gate then exposes the incorrect result.
            try (Connection gate = dataSource.getConnection()) {
                gate.setAutoCommit(false);
                try {
                    try (PreparedStatement lock = gate.prepareStatement(gateSql)) {
                        lock.setQueryTimeout(10);
                        lock.setLong(1, gateId);
                        try (var rows = lock.executeQuery()) {
                            assertTrue(rows.next(), "Gate row must exist");
                        }
                    }
                    futures.add(workers.submit(worker(first, ready, start, sessions)));
                    futures.add(workers.submit(worker(second, ready, start, sessions)));
                    assertTrue(ready.await(10, TimeUnit.SECONDS), "Both transactions must be ready");
                    List<DatabaseSession> active = List.copyOf(sessions);
                    assertEquals(2, active.size());
                    assertNotEquals(active.get(0).pid(), active.get(1).pid(), "Separate database connections");
                    assertNotEquals(active.get(0).transactionId(), active.get(1).transactionId(),
                            "Separate real database transactions");
                    start.countDown();
                    awaitBothBlocked(active);
                } finally {
                    gate.rollback();
                }
            }
            // execute() has committed/rolled back before a worker returns its outcome.
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            futures.forEach(future -> future.cancel(true));
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(25, TimeUnit.SECONDS), "Workers must stop before fixture cleanup");
        }
    }

    private Callable<Outcome> worker(Decision decision, CountDownLatch ready, CountDownLatch start,
                                     ConcurrentLinkedQueue<DatabaseSession> sessions) {
        return () -> {
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    decision.user(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + decision.user().role().name()))));
            SecurityContextHolder.setContext(context);
            try {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transaction.setTimeout(25);
                transaction.executeWithoutResult(status -> {
                    // Bound database waits as well as Java waits; a deadlock/timeout is a test failure.
                    jdbcTemplate.execute("SET LOCAL lock_timeout = '15s'");
                    jdbcTemplate.execute("SET LOCAL statement_timeout = '20s'");
                    sessions.add(jdbcTemplate.queryForObject("SELECT pg_backend_pid(), txid_current()",
                            (rs, row) -> new DatabaseSession(rs.getInt(1), rs.getLong(2))));
                    ready.countDown();
                    awaitStart(start);
                    decision.action().run();
                });
                return new Outcome(decision.user().userId(), decision.kind(), null);
            } catch (RuntimeException failure) {
                // Catch outside the transaction so even a rejected decision has finished rolling back.
                return new Outcome(decision.user().userId(), decision.kind(), failure);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            assertTrue(start.await(10, TimeUnit.SECONDS), "Start signal must arrive");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent start", interrupted);
        }
    }

    private void awaitBothBlocked(List<DatabaseSession> sessions) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long blocked = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE pid IN (?, ?) AND state = 'active' AND wait_event_type = 'Lock'
                    """, Long.class, sessions.get(0).pid(), sessions.get(1).pid());
            if (Long.valueOf(2).equals(blocked)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError("Both worker transactions must overlap while waiting for database locks");
    }

    private Outcome assertSingleDecision(List<Outcome> outcomes) {
        List<Outcome> successes = outcomes.stream().filter(outcome -> outcome.failure() == null).toList();
        assertEquals(1, successes.size(), () -> "Exactly one decision must commit: " + outcomes);
        Outcome loser = outcomes.stream().filter(outcome -> outcome.failure() != null).findFirst().orElseThrow();
        assertInstanceOf(ApprovalNotPendingException.class, loser.failure());
        return successes.get(0);
    }

    private BigDecimal readBalance() {
        return jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private PaymentState assertCompleted(PaymentFixture payment, Long actorId) {
        PaymentState state = readPayment(payment);
        assertEquals("completed", state.paymentStatus());
        assertEquals("approved", state.stepStatus());
        assertNotNull(state.executedAt());
        assertNotNull(state.decidedAt());
        assertNull(state.comment());
        assertNotNull(state.balanceBefore());
        assertEquals(state.balanceBefore().subtract(payment.amount()), state.balanceAfter());
        assertAudit(payment, "APPROVE_PAYMENT", actorId);
        return state;
    }

    private void assertAudit(PaymentFixture payment, String action, Long actorId) {
        List<AuditState> entries = jdbcTemplate.query("""
                SELECT action, user_id FROM audit_entries
                WHERE tenant_id = ? AND entity_type = 'PAYMENT' AND entity_id = ?
                """, (rs, row) -> new AuditState(rs.getString("action"), rs.getLong("user_id")),
                tenantId, payment.id());
        assertEquals(List.of(new AuditState(action, actorId)), entries);
    }

    private PaymentState readPayment(PaymentFixture payment) {
        return jdbcTemplate.queryForObject("""
                SELECT payment.status AS payment_status, payment.executed_at,
                       payment.balance_before_payment, payment.balance_after_payment,
                       step.status AS step_status, step.decided_at, step.comment
                FROM payments payment JOIN approval_steps step ON step.payment_id = payment.id
                WHERE payment.id = ? AND step.id = ?
                """, (rs, row) -> new PaymentState(
                        rs.getString("payment_status"), rs.getString("step_status"),
                        rs.getTimestamp("executed_at"), rs.getTimestamp("decided_at"), rs.getString("comment"),
                        rs.getBigDecimal("balance_before_payment"), rs.getBigDecimal("balance_after_payment")),
                payment.id(), payment.stepId());
    }

    private record PaymentFixture(Long id, Long stepId, BigDecimal amount) {}
    private record Decision(AuthenticatedUserContext user, String kind, Runnable action) {}
    private record Outcome(Long actorId, String kind, RuntimeException failure) {}
    private record DatabaseSession(int pid, long transactionId) {}
    private record AuditState(String action, Long actorId) {}
    private record PaymentState(String paymentStatus, String stepStatus, Timestamp executedAt,
                                Timestamp decidedAt, String comment, BigDecimal balanceBefore,
                                BigDecimal balanceAfter) {}
}
