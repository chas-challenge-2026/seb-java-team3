package se.comerit.seb.dto;

import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public record PaymentResponse(
        Long id,
        BigDecimal amount,
        String toIban,
        PaymentStatus status,
        LocalDateTime createdAt,
        Integer currentStepNumber,
        long remainingApprovals
) {
    // En "factory method" - ett rent, läsbart sätt att bygga ett
    // PaymentResponse direkt från en Payment-entity. Det håller
    // "hur vi gör om en entity till en DTO"-logiken på ett enda
    // ställe, i stället för utspritt i servicen.
    public static PaymentResponse from(Payment payment) {

        Optional<ApprovalStep> currentStep = payment.getApprovalSteps().stream()
                .filter(step -> step.getStatus() == ApprovalStepStatus.PENDING)
                .findFirst();

        long remainingApprovals = payment.getApprovalSteps().stream()
                .filter(step -> step.getStatus() == ApprovalStepStatus.PENDING)
                .count();

        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getToIban(),
                payment.getStatus(),
                payment.getCreatedAt(),
                currentStep.map(ApprovalStep::getStepNumber).orElse(null),
                remainingApprovals
        );
    }
}