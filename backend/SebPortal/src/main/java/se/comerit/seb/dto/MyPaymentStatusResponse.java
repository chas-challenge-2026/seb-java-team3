package se.comerit.seb.dto;

import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// approvalSteps visar vem som ska attestera och var betalningen står, så att initiatören vet vem
// som ska kontaktas om en betalning fastnar (UX-feedback).
public record MyPaymentStatusResponse(
        Long paymentId,
        String reference,
        BigDecimal amount,
        String currency,
        String fromAccountName,
        String toIban,
        String status,
        LocalDateTime createdAt,
        LocalDateTime executedAt,
        List<ApprovalStepSummary> approvalSteps
) {
    public record ApprovalStepSummary(
            Integer stepNumber,
            String attestantName,
            String status,
            LocalDateTime decidedAt
    ) {}

    // Namn som visas om attestanten inte längre finns, så att steget ändå syns.
    static final String UNKNOWN_ATTESTANT = "Okänd attestant";

    // fromAccountName är null om kontot inte finns (eller inte tillhör betalningens företag).
    public static MyPaymentStatusResponse from(Payment payment,
                                               Map<Long, String> accountNamesById,
                                               Map<Long, String> userNamesById) {
        List<ApprovalStepSummary> steps = payment.getApprovalSteps().stream()
                .map(step -> toSummary(step, userNamesById))
                .toList();

        return new MyPaymentStatusResponse(
                payment.getId(),
                payment.getReference(),
                payment.getAmount(),
                payment.getCurrency(),
                accountNamesById.get(payment.getFromAccountId()),
                payment.getToIban(),
                payment.getStatus().name(),
                payment.getCreatedAt(),
                payment.getExecutedAt(),
                steps
        );
    }

    private static ApprovalStepSummary toSummary(ApprovalStep step, Map<Long, String> userNamesById) {
        return new ApprovalStepSummary(
                step.getStepNumber(),
                userNamesById.getOrDefault(step.getAttestantId(), UNKNOWN_ATTESTANT),
                step.getStatus().name(),
                step.getDecidedAt()
        );
    }
}
