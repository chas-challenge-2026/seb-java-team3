package se.comerit.seb.dto;

import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.Account;
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
        String initiatorName,
        BigDecimal balanceAfterPayment
) {
    public static PendingApprovalResponse from(Payment payment, ApprovalStep step,
                                               Account fromAccount, String initiatorName) {
        BigDecimal balanceAfterPayment = fromAccount != null
                && fromAccount.getBalance() != null
                && payment.getCurrency().equals(fromAccount.getCurrency())
                ? fromAccount.getBalance().subtract(payment.getAmount()) : null;
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
                fromAccount == null ? null : fromAccount.getAccountName(),
                initiatorName,
                balanceAfterPayment
        );
    }
}
