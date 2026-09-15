package se.comerit.seb.dto;

import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PendingApprovalResponse(
        Long id,
        Long paymentId,
        String mottagare,
        String typ,
        BigDecimal belopp,
        String currency,
        String reference,
        LocalDateTime createdAt,
        String status
) {
    public static PendingApprovalResponse from(Payment payment, ApprovalStep step) {
        return new PendingApprovalResponse(
                step.getId(),
                payment.getId(),
                payment.getToIban(),
                "Betalning",
                payment.getAmount(),
                payment.getCurrency(),
                payment.getReference(),
                payment.getCreatedAt(),
                "Väntar"
        );
    }
}
