package se.comerit.seb.repository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.comerit.seb.domain.AuditEntry;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findTop200ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<AuditEntry> findTop200ByTenantIdAndActionInOrderByCreatedAtDesc(
            Long tenantId,
            Collection<String> actions);

    @Query("""
            SELECT entry
            FROM AuditEntry entry
            WHERE entry.tenantId = :tenantId
              AND UPPER(entry.entityType) = 'PAYMENT'
              AND entry.entityId = :paymentId
            ORDER BY entry.createdAt ASC, entry.id ASC
            """)
    List<AuditEntry> findPaymentEvents(
            @Param("tenantId") Long tenantId,
            @Param("paymentId") Long paymentId);
}
