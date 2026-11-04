package se.comerit.seb.dto;

import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// fromAccountName och initiatorName ger attestanten underlag för beslutet: vilket konto som belastas,
// och vem som skapade betalningen och kan svara på frågor om den (UX-feedback). Null om de inte
// går att slå upp inom attestantens företag. stepNumber och totalSteps visar var i kedjan steget ligger
// ("Attest 1 av 2"), och att betalningen inte genomförs förrän sista steget är godkänt.
// previousApprovals visar vem som redan har godkänt, så att nästa attestant vet vem som granskat före.
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
        BigDecimal balanceAfterPayment,
        Integer stepNumber,
        int totalSteps,
        List<PreviousApproval> previousApprovals
) {
    public record PreviousApproval(Integer stepNumber, String approverName) {}

    // userNamesById innehåller bara det egna företagets användare (R-04).
    public static PendingApprovalResponse from(Payment payment, ApprovalStep step,
                                               Account fromAccount, Map<Long, String> userNamesById) {
        BigDecimal balanceAfterPayment = fromAccount != null
                && fromAccount.getBalance() != null
                && payment.getCurrency().equals(fromAccount.getCurrency())
                ? fromAccount.getBalance().subtract(payment.getAmount()) : null;
        List<PreviousApproval> previousApprovals = payment.getApprovalSteps().stream()
                .filter(candidate -> candidate.getStatus() == ApprovalStepStatus.APPROVED)
                .sorted(Comparator.comparing(ApprovalStep::getStepNumber))
                .map(approved -> new PreviousApproval(
                        approved.getStepNumber(),
                        userNamesById.getOrDefault(approved.getDecidedBy(), MyPaymentStatusResponse.UNKNOWN_ATTESTANT)))
                .toList();
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
                userNamesById.get(payment.getCreatedBy()),
                balanceAfterPayment,
                step.getStepNumber(),
                payment.getApprovalSteps().size(),
                previousApprovals
        );
    }
}
