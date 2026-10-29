package se.comerit.seb.dto;

import se.comerit.seb.domain.AuditEntry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// fromAccountName: kontot betalningen dras från (null om det inte går att slå upp inom företaget).
// autoApproved: betalningen genomfördes utan atteststeg, eftersom beloppet låg under tröskeln.
// comment: attestantens meddelande vid godkännande eller avvisning (#228), annars null.
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
        String userName,
        BigDecimal balanceBeforePayment,
        BigDecimal balanceAfterPayment,
        String fromAccountName,
        boolean autoApproved,
        String comment
) {
    public static AuditEntryResponse from(
            AuditEntry entry,
            String userName,
            String status,
            String reference,
            BigDecimal amount,
            String currency,
            BigDecimal balanceBeforePayment,
            BigDecimal balanceAfterPayment,
            String fromAccountName,
            boolean autoApproved
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
                userName,
                balanceBeforePayment,
                balanceAfterPayment,
                fromAccountName,
                autoApproved,
                entry.getComment()
        );
    }
}
