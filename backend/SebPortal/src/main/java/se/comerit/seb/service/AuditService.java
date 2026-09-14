package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.AuditEntryResponse;
import se.comerit.seb.repository.AuditRepository;
import se.comerit.seb.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private static final String SYSTEM_USER_NAME = "Systemet";

    private final AuditRepository auditRepository;
    private final UserRepository userRepository;

    public AuditService(AuditRepository auditRepository, UserRepository userRepository) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    public AuditEntry record(Long tenantId, Long userId, String action,
                             String entityType, Long entityId, String description) {
        AuditEntry entry = new AuditEntry(
                tenantId,
                userId,
                action,
                entityType,
                entityId,
                description
        );

        return auditRepository.save(entry);
    }

    public List<AuditEntryResponse> getAuditEntries(Long tenantId) {
        List<AuditEntry> entries = auditRepository.findTop200ByTenantIdOrderByCreatedAtDesc(tenantId);

        List<Long> userIds = entries.stream()
                .map(AuditEntry::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> userNamesById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        return entries.stream()
                .map(entry -> AuditEntryResponse.from(
                        entry,
                        userNamesById.getOrDefault(entry.getUserId(), SYSTEM_USER_NAME)))
                .collect(Collectors.toList());
    }
}