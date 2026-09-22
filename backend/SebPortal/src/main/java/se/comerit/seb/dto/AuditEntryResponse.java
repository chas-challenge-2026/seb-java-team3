package se.comerit.seb.dto;

import se.comerit.seb.domain.AuditEntry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuditEntryResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        String description,
        String status,
        String reference,
        BigDecimal amount,
        String currency,
        LocalDateTime createdAt,
        String userName
) {
    public static AuditEntryResponse from(
            AuditEntry entry,
            String userName,
            String status,
            String reference,
            BigDecimal amount,
            String currency
    ) {
        return new AuditEntryResponse(
                entry.getId(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDescription(),
                status,
                reference,
                amount,
                currency,
                entry.getCreatedAt(),
                userName
        );
    }
}
