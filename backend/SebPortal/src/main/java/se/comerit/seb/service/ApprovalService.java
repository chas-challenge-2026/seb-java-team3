package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ApprovalService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public ApprovalService(PaymentRepository paymentRepository,
                           AccountRepository accountRepository,
                           AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void approve(Long approvalStepId, Long actorId) {
        if (approvalStepId == null) {
            throw new IllegalArgumentException("Approval step id is required");
        }

        if (actorId == null) {
            throw new IllegalArgumentException("Actor id is required");
        }

        Payment payment = paymentRepository.findByApprovalStepIdForUpdate(approvalStepId)
                .orElseThrow(() -> new IllegalArgumentException("Approval step not found: " + approvalStepId));

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Payment is not pending approval: " + payment.getId());
        }

        ApprovalStep approvalStep = payment.getApprovalSteps().stream()
                .filter(step -> Objects.equals(step.getId(), approvalStepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Approval step not found: " + approvalStepId));

        if (approvalStep.getStatus() != ApprovalStepStatus.PENDING) {
            throw new IllegalStateException("Approval step is not pending: " + approvalStepId);
        }

        if (!Objects.equals(approvalStep.getAttestantId(), actorId)) {
            throw new IllegalStateException("Approval step is not assigned to actor: " + actorId);
        }

        approvalStep.setStatus(ApprovalStepStatus.APPROVED);
        approvalStep.setDecidedAt(LocalDateTime.now());

        boolean allApproved = payment.getApprovalSteps().stream()
                .allMatch(step -> step.getStatus() == ApprovalStepStatus.APPROVED);

        if (allApproved) {
            Integer accountId = Math.toIntExact(payment.getFromAccountId());
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

            if (account.getBalance() == null) {
                throw new IllegalStateException("Account balance is missing: " + accountId);
            }

            if (payment.getAmount() == null) {
                throw new IllegalStateException("Payment amount is missing: " + payment.getId());
            }

            account.setBalance(account.getBalance().subtract(payment.getAmount()));
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExecutedAt(LocalDateTime.now());

            String description = "Betalning godkänd: %s %s till %s (status: %s)".formatted(
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getToIban(),
                    payment.getStatus());

            auditService.record(
                    payment.getTenantId(),
                    actorId,
                    "APPROVE_PAYMENT",
                    "PAYMENT",
                    payment.getId(),
                    description
            );
        }
    }
}
