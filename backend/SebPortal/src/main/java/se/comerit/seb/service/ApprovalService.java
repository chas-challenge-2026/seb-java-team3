package se.comerit.seb.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.exception.ApprovalStepAccessDeniedException;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ApprovalService {

    // Samma text som PaymentService använder vid skapande: saknat konto, ogiltigt id och ett annat
    // företags konto ska vara omöjliga att skilja åt, så att konto-id hos andra kunder inte
    // går att kartlägga.
    private static final String FROM_ACCOUNT_NOT_AVAILABLE =
            "Från-kontot finns inte eller tillhör inte ditt företag";

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

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional
    public void approveForUser(Long approvalStepId, AuthenticatedUserContext user) {
        approveInternal(approvalStepId, user.userId(), user);
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional
    public void approve(Long approvalStepId, Long actorId) {
        approveInternal(approvalStepId, actorId, null);
    }

    private void approveInternal(Long approvalStepId, Long actorId, AuthenticatedUserContext user) {
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

        if (user != null && (user.tenantId() == null || !Objects.equals(payment.getTenantId(), user.tenantId()))) {
            throw new ApprovalStepAccessDeniedException("Payment is not available for this company");
        }
        if (!(user != null && user.isAdmin()) && !Objects.equals(approvalStep.getAttestantId(), actorId)) {
            throw new ApprovalStepAccessDeniedException(
                    "Approval step " + approvalStepId + " is not assigned to actor " + actorId);
        }

        boolean earlierStepPending = payment.getApprovalSteps().stream()
                .anyMatch(step -> step.getStepNumber() < approvalStep.getStepNumber()
                        && step.getStatus() == ApprovalStepStatus.PENDING);

        if (earlierStepPending) {
            throw new IllegalStateException(
                    "Cannot approve step " + approvalStepId + ": an earlier step is still pending");
        }

        approvalStep.setStatus(ApprovalStepStatus.APPROVED);
        approvalStep.setDecidedAt(LocalDateTime.now());

        boolean allApproved = payment.getApprovalSteps().stream()
                .allMatch(step -> step.getStatus() == ApprovalStepStatus.APPROVED);

        if (allApproved) {
            Account account = findAndLockAccountOfPaymentTenant(payment);

            if (account.getBalance() == null) {
                throw new IllegalStateException("Account balance is missing: " + payment.getFromAccountId());
            }

            if (payment.getAmount() == null) {
                throw new IllegalStateException("Payment amount is missing: " + payment.getId());
            }

            if (payment.getAmount().compareTo(account.getBalance()) > 0) {
                throw new IllegalArgumentException("Beloppet överstiger tillgängligt saldo.");
            }
            account.setBalance(account.getBalance().subtract(payment.getAmount()));
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExecutedAt(LocalDateTime.now());

            String description = "Betalning godkänd: %s %s till %s".formatted(
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getToIban());

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

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional
    public void rejectForUser(Long approvalStepId, AuthenticatedUserContext user, String comment) {
        rejectInternal(approvalStepId, user.userId(), comment, user);
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional
    public void reject(Long approvalStepId, Long actorId, String comment) {
        rejectInternal(approvalStepId, actorId, comment, null);
    }

    private void rejectInternal(Long approvalStepId, Long actorId, String comment, AuthenticatedUserContext user) {
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

        if (user != null && (user.tenantId() == null || !Objects.equals(payment.getTenantId(), user.tenantId()))) {
            throw new ApprovalStepAccessDeniedException("Payment is not available for this company");
        }
        if (!(user != null && user.isAdmin()) && !Objects.equals(approvalStep.getAttestantId(), actorId)) {
            throw new ApprovalStepAccessDeniedException(
                    "Approval step " + approvalStepId + " is not assigned to actor " + actorId);
        }

        approvalStep.setStatus(ApprovalStepStatus.REJECTED);
        approvalStep.setDecidedAt(LocalDateTime.now());
        approvalStep.setComment(comment);

        payment.getApprovalSteps().stream()
                .filter(step -> step.getStatus() == ApprovalStepStatus.PENDING)
                .forEach(step -> {
                    step.setStatus(ApprovalStepStatus.REJECTED);
                    step.setDecidedAt(LocalDateTime.now());
                });

        payment.setStatus(PaymentStatus.REJECTED);

        String description = "Betalning avvisad: %s %s till %s".formatted(
                payment.getAmount(),
                payment.getCurrency(),
                payment.getToIban());

        auditService.record(
                payment.getTenantId(),
                actorId,
                "REJECT_PAYMENT",
                "PAYMENT",
                payment.getId(),
                description
        );
    }

    @Transactional(readOnly = true)
    public int countPendingByAttestant(AuthenticatedUserContext user) {
        var payments = user.isAdmin()
                ? paymentRepository.findPendingApprovalsForTenant(user.tenantId())
                : paymentRepository.findPendingApprovalsForAttestant(user.tenantId(), user.userId());
        return (int) payments
                .stream()
                .flatMap(payment -> payment.getApprovalSteps().stream())
                .filter(step -> step.getStatus() == ApprovalStepStatus.PENDING)
                .filter(step -> user.isAdmin() || user.userId().equals(step.getAttestantId()))
                .count();
    }

    // Hämtar kontot som ska belastas och låser raden tills transaktionen är klar, men bara om
    // kontot tillhör betalningens företag (R-04).
    //  - PaymentService kontrollerar redan detta när betalningen skapas. Här kontrolleras det en
    //    gång till, där pengarna faktiskt flyttas, eftersom betalningar som skapades innan den
    //    kontrollen fanns (t.ex. väntande betalningar i stage) kan peka på ett annat företags konto.
    //  - Låset gör att två godkännanden mot samma konto går efter varandra i stället för att båda
    //    läsa samma saldo och skriva över varandras avdrag. Betalningsraden är redan låst av
    //    findByApprovalStepIdForUpdate; låsordningen är alltid betalning före konto.
    private Account findAndLockAccountOfPaymentTenant(Payment payment) {
        Long fromAccountId = payment.getFromAccountId();

        // accounts.id är INT i databasen. Ett Long utanför intervallet får inte "slå runt" till
        // ett annat konto-id (4 294 967 297 blir 1 vid omvandling till int).
        boolean validId = fromAccountId != null && fromAccountId > 0 && fromAccountId <= Integer.MAX_VALUE;
        if (!validId) {
            throw new IllegalArgumentException(FROM_ACCOUNT_NOT_AVAILABLE);
        }

        return accountRepository
                .findByIdAndTenantIdForUpdate(fromAccountId.intValue(), payment.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException(FROM_ACCOUNT_NOT_AVAILABLE));
    }
}