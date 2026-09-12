package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.repository.AuditRepository;
import java.util.List;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public AuditEntry record(Long tenantId, Long userId, String action,
                             String entityType, Long entityId, String description) {
        AuditEntry entry = new AuditEntry(tenantId, userId, action, entityType, entityId, description);
        return auditRepository.save(entry);
    }

    public List<AuditEntry> getAuditEntries(Long tenantId) {
        return auditRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}