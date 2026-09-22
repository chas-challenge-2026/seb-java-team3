package se.comerit.seb.dto;

import se.comerit.seb.domain.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MyPaymentStatusResponse(
        Long paymentId,
        String reference,
        BigDecimal amount,
        String currency,
        String toIban,
        String status,
        LocalDateTime createdAt,
        LocalDateTime executedAt
) {
    public static MyPaymentStatusResponse from(Payment payment) {
        return new MyPaymentStatusResponse(
                payment.getId(),
                payment.getReference(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getToIban(),
                payment.getStatus().name(),
                payment.getCreatedAt(),
                payment.getExecutedAt()
        );
    }
}
