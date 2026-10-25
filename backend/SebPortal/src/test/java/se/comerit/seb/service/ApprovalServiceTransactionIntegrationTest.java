package se.comerit.seb.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@Testcontainers
@WithMockUser(roles = "ATTESTANT")
class ApprovalServiceTransactionIntegrationTest {

    private static final BigDecimal STARTING_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("200.00");
    private static final BigDecimal FINAL_BALANCE = new BigDecimal("800.00");
    private static final String TO_IBAN = "SE8550000000054910000003";
    private static final String FAILURE_MARKER = "issue193_failure_after_all_database_writes";

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

    private Long tenantId;
    private Long actorId;
    private Long accountId;
    private Long paymentId;
    private Long approvalStepId;

    @BeforeEach
    void createFixture() {
        // Each JDBC insert commits before approve starts its own service transaction.
        tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name) VALUES (?) RETURNING id",
                Long.class,
                "Issue 193 test tenant");
        actorId = jdbcTemplate.queryForObject("""
                INSERT INTO users (tenant_id, name, email, password_hash, role)
                VALUES (?, ?, ?, ?, 'attestant')
                RETURNING id
                """,
                Long.class,
                tenantId,
                "Issue 193 attestant",
                "issue193-attestant-" + tenantId + "@seb.test",
                "unused-test-password-hash");
        accountId = jdbcTemplate.queryForObject("""
                INSERT INTO accounts (tenant_id, account_name, iban, balance, currency)
                VALUES (?, ?, ?, ?, 'SEK')
                RETURNING id
                """,
                Long.class,
                tenantId,
                "Issue 193 account",
                "SE4550000000058398257466",
                STARTING_BALANCE);
        paymentId = jdbcTemplate.queryForObject("""
                INSERT INTO payments
                    (tenant_id, from_account_id, to_iban, amount, currency, reference, status, created_by)
                VALUES (?, ?, ?, ?, 'SEK', ?, 'pending_approval', ?)
                RETURNING id
                """,
                Long.class,
                tenantId,
                accountId,
                TO_IBAN,
                PAYMENT_AMOUNT,
                "Issue 193 transaction test",
                actorId);
        approvalStepId = jdbcTemplate.queryForObject("""
                INSERT INTO approval_steps (payment_id, attestant_id, step_number, status)
                VALUES (?, ?, 1, 'pending')
                RETURNING id
                """,
                Long.class,
                paymentId,
                actorId);
    }

    @AfterEach
    void removeFixture() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS issue193_fail_approval_commit ON audit_entries");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS issue193_fail_approval_commit()");

        if (paymentId != null) {
            jdbcTemplate.update("""
                    DELETE FROM audit_entries
                    WHERE tenant_id = ? AND entity_type = 'PAYMENT' AND entity_id = ?
                    """, tenantId, paymentId);
            jdbcTemplate.update("DELETE FROM approval_steps WHERE payment_id = ?", paymentId);
            jdbcTemplate.update("DELETE FROM payments WHERE id = ?", paymentId);
        }
        if (accountId != null) {
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
        }
        if (actorId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
        }
        if (tenantId != null) {
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    @Test
    void rollback_on_failure() {
        installLateDatabaseFailure();

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> approvalService.approve(approvalStepId, actorId));

        SQLException databaseFailure = findSqlException(failure);
        assertEquals("P0001", databaseFailure.getSQLState());
        assertTrue(databaseFailure.getMessage().contains(FAILURE_MARKER), databaseFailure::getMessage);

        // There is no test transaction: these JDBC reads happen after the service rolled back.
        ApprovalState state = readApprovalState();
        assertEquals(STARTING_BALANCE, state.balance());
        assertEquals("pending_approval", state.paymentStatus());
        assertNull(state.executedAt());
        assertEquals("pending", state.stepStatus());
        assertNull(state.decidedAt());
        assertNull(state.comment());
        assertEquals(0L, state.auditCount());
    }

    @Test
    void commits_balance_and_audit_on_success() {
        approvalService.approve(approvalStepId, actorId);

        // Returning from the Spring proxy includes committing the service transaction.
        ApprovalState state = readApprovalState();
        assertEquals(FINAL_BALANCE, state.balance());
        assertEquals("completed", state.paymentStatus());
        assertNotNull(state.executedAt());
        assertEquals("approved", state.stepStatus());
        assertNotNull(state.decidedAt());
        assertNull(state.comment());
        assertEquals(1L, state.auditCount());

        AuditSnapshot audit = jdbcTemplate.queryForObject("""
                SELECT tenant_id, user_id, action, entity_type, entity_id, description, created_at
                FROM audit_entries
                WHERE tenant_id = ? AND entity_type = 'PAYMENT' AND entity_id = ?
                """,
                (resultSet, rowNumber) -> new AuditSnapshot(
                        resultSet.getLong("tenant_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("action"),
                        resultSet.getString("entity_type"),
                        resultSet.getLong("entity_id"),
                        resultSet.getString("description"),
                        resultSet.getTimestamp("created_at")),
                tenantId,
                paymentId);

        assertNotNull(audit);
        assertEquals(tenantId.longValue(), audit.tenantId());
        assertEquals(actorId.longValue(), audit.userId());
        assertEquals("APPROVE_PAYMENT", audit.action());
        assertEquals("PAYMENT", audit.entityType());
        assertEquals(paymentId.longValue(), audit.entityId());
        assertEquals("Betalning godkänd: 200.00 SEK till " + TO_IBAN, audit.description());
        assertNotNull(audit.createdAt());
    }

    private void installLateDatabaseFailure() {
        // AuditEntry uses IDENTITY, so its INSERT occurs before JPA flushes dirty account/payment/step
        // updates. A deferred trigger checks all four database writes at commit before forcing failure.
        jdbcTemplate.execute("""
                CREATE FUNCTION issue193_fail_approval_commit() RETURNS trigger AS $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1
                        FROM payments payment
                        JOIN accounts account ON account.id = payment.from_account_id
                        JOIN approval_steps step ON step.payment_id = payment.id
                        JOIN audit_entries audit ON audit.id = NEW.id
                        WHERE payment.id = NEW.entity_id
                          AND payment.id = %d
                          AND account.id = %d
                          AND step.id = %d
                          AND account.balance = %s
                          AND payment.status = 'completed'
                          AND payment.executed_at IS NOT NULL
                          AND step.status = 'approved'
                          AND step.decided_at IS NOT NULL
                          AND audit.tenant_id = payment.tenant_id
                          AND audit.user_id = %d
                          AND audit.action = 'APPROVE_PAYMENT'
                          AND audit.entity_type = 'PAYMENT'
                          AND audit.entity_id = payment.id
                    ) THEN
                        RAISE EXCEPTION 'issue193_expected_database_writes_missing'
                            USING ERRCODE = 'P0001';
                    END IF;

                    RAISE EXCEPTION '%s' USING ERRCODE = 'P0001';
                END;
                $$ LANGUAGE plpgsql;
                """.formatted(paymentId, accountId, approvalStepId, FINAL_BALANCE.toPlainString(),
                actorId, FAILURE_MARKER));
        jdbcTemplate.execute("""
                CREATE CONSTRAINT TRIGGER issue193_fail_approval_commit
                AFTER INSERT ON audit_entries
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW
                WHEN (NEW.action = 'APPROVE_PAYMENT'
                      AND NEW.entity_type = 'PAYMENT'
                      AND NEW.entity_id = %d
                      AND NEW.tenant_id = %d)
                EXECUTE FUNCTION issue193_fail_approval_commit()
                """.formatted(paymentId, tenantId));
    }

    private ApprovalState readApprovalState() {
        return jdbcTemplate.queryForObject("""
                SELECT account.balance,
                       payment.status AS payment_status,
                       payment.executed_at,
                       step.status AS step_status,
                       step.decided_at,
                       step.comment,
                       (SELECT COUNT(*) FROM audit_entries audit
                        WHERE audit.tenant_id = payment.tenant_id
                          AND audit.entity_type = 'PAYMENT'
                          AND audit.entity_id = payment.id) AS audit_count
                FROM payments payment
                JOIN accounts account ON account.id = payment.from_account_id
                JOIN approval_steps step ON step.payment_id = payment.id
                WHERE payment.id = ? AND step.id = ?
                """,
                (resultSet, rowNumber) -> new ApprovalState(
                        resultSet.getBigDecimal("balance"),
                        resultSet.getString("payment_status"),
                        resultSet.getTimestamp("executed_at"),
                        resultSet.getString("step_status"),
                        resultSet.getTimestamp("decided_at"),
                        resultSet.getString("comment"),
                        resultSet.getLong("audit_count")),
                paymentId,
                approvalStepId);
    }

    private static SQLException findSqlException(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        throw new AssertionError("Expected a PostgreSQL failure", failure);
    }

    private record ApprovalState(
            BigDecimal balance,
            String paymentStatus,
            Timestamp executedAt,
            String stepStatus,
            Timestamp decidedAt,
            String comment,
            long auditCount
    ) {}

    private record AuditSnapshot(
            long tenantId,
            long userId,
            String action,
            String entityType,
            long entityId,
            String description,
            Timestamp createdAt
    ) {}
}
