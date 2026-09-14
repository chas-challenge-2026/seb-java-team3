package se.comerit.seb.dto;

import se.comerit.seb.domain.AuditEntry;

import java.time.LocalDateTime;

public record AuditEntryResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        String description,
        LocalDateTime createdAt,
        String userName
) {
    public static AuditEntryResponse from(AuditEntry entry, String userName) {
        return new AuditEntryResponse(
                entry.getId(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDescription(),
                entry.getCreatedAt(),
                userName
        );
    }
}
