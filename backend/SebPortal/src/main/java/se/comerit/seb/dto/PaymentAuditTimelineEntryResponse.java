package se.comerit.seb.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// comment: attestantens meddelande vid godkännande eller avvisning (#228). Null för övriga händelser och
// när attestanten inte skrev något.
public record PaymentAuditTimelineEntryResponse(
        int order,
        String sequence,
        String actor,
        String eventType,
        String description,
        String comment,
        LocalDateTime timestamp,
        Integer stepNumber,
        String status,
        String reference,
        BigDecimal amount,
        String currency,
        String toIban
) {}
