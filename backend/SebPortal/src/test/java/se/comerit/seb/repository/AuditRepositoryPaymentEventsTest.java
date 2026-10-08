package se.comerit.seb.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.AuditEntry;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// #196: findPaymentEvents mot en riktig SQL-motor (H2 i minnet, samma upplägg som
// AccountRepositoryTenantTest). Tie-breaken vid samma tidsstämpel testas i
// AuditServiceTimelineTest: H2 lämnar ut raderna i id-ordning även utan "entry.id ASC",
// så ett sådant test här skulle vara grönt vare sig tie-breaken finns eller inte.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuditRepositoryPaymentEventsTest {

    private static final Long TENANT_ID = 1L;
    private static final Long OTHER_TENANT_ID = 2L;
    private static final Long PAYMENT_ID = 100L;
    private static final Long OTHER_PAYMENT_ID = 101L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 8, 9, 0);

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void findPaymentEvents_returnsTheEventsInChronologicalOrder() {
        // created_at sätts av databasen och går inte att skriva via entiteten, så raderna
        // läggs in med SQL. Godkännandet läggs in FÖRST (lägre id) men har senare tid,
        // så det är ORDER BY created_at som ger rätt ordning, inte id-ordningen.
        insert(TENANT_ID, PAYMENT_ID, "APPROVE_PAYMENT", T0.plusMinutes(5));
        insert(TENANT_ID, PAYMENT_ID, "CREATE_PAYMENT", T0);

        List<String> actions = auditRepository.findPaymentEvents(TENANT_ID, PAYMENT_ID).stream()
                .map(AuditEntry::getAction)
                .toList();

        assertEquals(List.of("CREATE_PAYMENT", "APPROVE_PAYMENT"), actions);
    }

    @Test
    void findPaymentEvents_onlyReturnsEventsForThatPaymentAndTenant() {
        insert(TENANT_ID, PAYMENT_ID, "CREATE_PAYMENT", T0);
        insert(TENANT_ID, OTHER_PAYMENT_ID, "CREATE_PAYMENT", T0);
        insert(OTHER_TENANT_ID, PAYMENT_ID, "CREATE_PAYMENT", T0);

        List<AuditEntry> events = auditRepository.findPaymentEvents(TENANT_ID, PAYMENT_ID);

        assertEquals(1, events.size());
        assertEquals(TENANT_ID, events.get(0).getTenantId());
        assertEquals(PAYMENT_ID, events.get(0).getEntityId());
    }

    private void insert(Long tenantId, Long paymentId, String action, LocalDateTime createdAt) {
        jdbc.update("""
                insert into audit_entries (tenant_id, user_id, action, entity_type, entity_id, description, created_at)
                values (?, ?, ?, 'PAYMENT', ?, ?, ?)
                """, tenantId, 1L, action, paymentId, action, createdAt);
    }
}
