package se.comerit.seb.repository;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// #228: V10 lägger till audit_entries.comment och för över meddelandet från avvisningar som gjordes före V10,
// så att de också visar sitt meddelande i historiken. Testet kör de riktiga migreringarna mot H2 i
// PostgreSQL-läge: först till V9, sedan läggs beslut in som de såg ut då, och till sist körs V10.
class V10AuditCommentMigrationTest {

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v10-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);

        migrateTo(dataSource, "9");

        // 101: avvisad med meddelande. 102: avvisad utan meddelande. 103: godkänd (före V10 utan meddelande).
        // 104: två steg, där steg 1 avvisade med meddelande och steg 2 därför avvisades automatiskt utan.
        jdbc.update("""
                INSERT INTO payments (id, tenant_id, from_account_id, to_iban, amount, status, created_by) VALUES
                    (101, 1, 1, 'SE8550000000054910000003', 6000.00, 'rejected', 1),
                    (102, 1, 1, 'SE8550000000054910000003', 7000.00, 'rejected', 1),
                    (103, 1, 1, 'SE8550000000054910000003', 8000.00, 'completed', 1),
                    (104, 1, 1, 'SE8550000000054910000003', 9000.00, 'rejected', 1)
                """);
        jdbc.update("""
                INSERT INTO approval_steps (payment_id, attestant_id, step_number, status, comment) VALUES
                    (101, 2, 1, 'rejected', 'Fel mottagare'),
                    (102, 2, 1, 'rejected', NULL),
                    (103, 2, 1, 'approved', NULL),
                    (104, 2, 1, 'rejected', 'Saknar underlag'),
                    (104, 2, 2, 'rejected', NULL)
                """);
        jdbc.update("""
                INSERT INTO audit_entries (tenant_id, user_id, action, entity_type, entity_id, description) VALUES
                    (1, 1, 'CREATE_PAYMENT', 'PAYMENT', 101, 'Betalning skapad'),
                    (1, 2, 'REJECT_PAYMENT', 'PAYMENT', 101, 'Betalning avvisad. Kommentar: Fel mottagare'),
                    (1, 2, 'REJECT_PAYMENT', 'PAYMENT', 102, 'Betalning avvisad'),
                    (1, 2, 'APPROVE_PAYMENT', 'PAYMENT', 103, 'Betalning godkänd'),
                    (1, 2, 'REJECT_PAYMENT', 'payment', 104, 'Betalning avvisad. Kommentar: Saknar underlag')
                """);

        migrateTo(dataSource, "10");
    }

    private static void migrateTo(DataSource dataSource, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    @Test
    void rejectionWithAMessage_getsTheMessageFromItsStep() {
        assertEquals("Fel mottagare", commentOf("REJECT_PAYMENT", 101));
    }

    @Test
    void rejectionWithoutAMessage_staysWithoutOne() {
        assertNull(commentOf("REJECT_PAYMENT", 102));
    }

    // Steg 2 avvisades utan meddelande när steg 1 avvisade; meddelandet ska komma från steget som beslutade.
    // entity_type 'payment' med små bokstäver räknas också, som i AuditService.
    @Test
    void twoStepRejection_getsTheMessageOfTheStepThatRejected() {
        assertEquals("Saknar underlag", commentOf("REJECT_PAYMENT", 104));
    }

    @Test
    void otherEvents_areNotTouched() {
        assertNull(commentOf("CREATE_PAYMENT", 101));
        assertNull(commentOf("APPROVE_PAYMENT", 103));
    }

    // Samma gräns som approval_steps.comment och ApprovalService.MAX_COMMENT_LENGTH
    @Test
    void commentColumnHoldsTheSameLengthAsTheStepComment() {
        Integer length = jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_name = 'audit_entries' AND column_name = 'comment'
                """, Integer.class);

        assertEquals(255, length);
    }

    private static String commentOf(String action, long paymentId) {
        return jdbc.queryForObject(
                "SELECT comment FROM audit_entries WHERE action = ? AND entity_id = ?",
                String.class, action, paymentId);
    }
}
