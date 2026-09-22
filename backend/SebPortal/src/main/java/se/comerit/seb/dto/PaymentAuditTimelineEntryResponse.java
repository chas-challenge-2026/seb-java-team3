package se.comerit.seb.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentAuditTimelineEntryResponse(
        int order,
        String sequence,
        String actor,
        String eventType,
        String description,
        LocalDateTime timestamp,
        Integer stepNumber,
        String status,
        String reference,
        BigDecimal amount,
        String currency,
        String toIban
) {}
