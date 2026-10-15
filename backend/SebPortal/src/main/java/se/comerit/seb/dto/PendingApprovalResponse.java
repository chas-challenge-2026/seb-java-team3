package se.comerit.seb.dto;

import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// fromAccountName och initiatorName ger attestanten underlag för beslutet: vilket konto som belastas,
// och vem som skapade betalningen och kan svara på frågor om den (UX-feedback). Null om de inte
// går att slå upp inom attestantens företag.
public record PendingApprovalResponse(
        Long id,
        Long paymentId,
        String mottagare,
        String typ,
        BigDecimal belopp,
        String currency,
        String reference,
        LocalDateTime createdAt,
        String status,
        String fromAccountName,
        String initiatorName
) {
    public static PendingApprovalResponse from(Payment payment, ApprovalStep step,
                                               String fromAccountName, String initiatorName) {
        return new PendingApprovalResponse(
                step.getId(),
                payment.getId(),
                payment.getToIban(),
                "Betalning",
                payment.getAmount(),
                payment.getCurrency(),
                payment.getReference(),
                payment.getCreatedAt(),
                "Väntar",
                fromAccountName,
                initiatorName
        );
    }
}
