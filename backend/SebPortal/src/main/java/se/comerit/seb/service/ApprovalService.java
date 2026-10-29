package se.comerit.seb.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.PendingApprovalResponse;
import se.comerit.seb.exception.ApprovalNotPendingException;
import se.comerit.seb.exception.ApprovalStepAccessDeniedException;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ApprovalService {

    // Samma text som PaymentService använder vid skapande: saknat konto, ogiltigt id och ett annat
    // företags konto ska vara omöjliga att skilja åt, så att konto-id hos andra kunder inte
    // går att kartlägga.
    private static final String FROM_ACCOUNT_NOT_AVAILABLE =
            "Från-kontot finns inte eller tillhör inte ditt företag";

    // Samma gräns som kolumnen approval_steps.comment (V3), så att ett för långt meddelande ger 400 i stället
    // för ett databasfel. Gäller både godkännande och avvisning.
    static final int MAX_COMMENT_LENGTH = 255;

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public ApprovalService(PaymentRepository paymentRepository,
                           AccountRepository accountRepository,
                           AuditService auditService,
                           UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    // Attestkorgen: väntande steg för den inloggade attestanten (en ADMIN ser hela företagets).
    // Kontonamn och initiatörens namn slås upp en gång för hela listan, och bara inom företaget,
    // så att ett annat företags konton eller användare aldrig namnges (R-04).
    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<PendingApprovalResponse> getPendingApprovals(AuthenticatedUserContext user) {
        List<Payment> payments = user.isAdmin()
                ? paymentRepository.findPendingApprovalsForTenant(user.tenantId())
                : paymentRepository.findPendingApprovalsForAttestant(user.tenantId(), user.userId());

        Map<Long, Account> accountsById = accountRepository.findByTenantIdOrderByIdAsc(user.tenantId()).stream()
                .collect(Collectors.toMap(account -> account.getId().longValue(), account -> account));

        List<Long> initiatorIds = payments.stream()
                .map(Payment::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> initiatorNamesById = userRepository.findAllById(initiatorIds).stream()
                .filter(initiator -> user.tenantId().equals(initiator.getTenantId()))
                .collect(Collectors.toMap(User::getId, User::getName));

        return payments.stream()
                .flatMap(payment -> payment.getApprovalSteps().stream()
                        .filter(step -> step.getStatus() == ApprovalStepStatus.PENDING)
                        .filter(step -> user.isAdmin() || user.userId().equals(step.getAttestantId()))
                        .map(step -> PendingApprovalResponse.from(
                                payment,
                                step,
                                accountsById.get(payment.getFromAccountId()),
                                initiatorNamesById.get(payment.getCreatedBy()))))
                .toList();
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional
    public void approveForUser(Long approvalStepId, AuthenticatedUserContext user, String comment) {
        approveInternal(approvalStepId, user.userId(), comment, user);
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional
    public void approve(Long approvalStepId, Long actorId, String comment) {
        approveInternal(approvalStepId, actorId, comment, null);
    }

    private void approveInternal(Long approvalStepId, Long actorId, String rawComment, AuthenticatedUserContext user) {
        String comment = normalizeComment(rawComment);

        if (approvalStepId == null) {
            throw new IllegalArgumentException("Approval step id is required");
        }

        if (actorId == null) {
            throw new IllegalArgumentException("Actor id is required");
        }

        Payment payment = findPaymentOfStepForUpdate(approvalStepId, user);

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new ApprovalNotPendingException("Betalningen är redan behandlad. Ladda om sidan för att se det senaste.");
        }

        ApprovalStep approvalStep = payment.getApprovalSteps().stream()
                .filter(step -> Objects.equals(step.getId(), approvalStepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Approval step not found: " + approvalStepId));

        if (approvalStep.getStatus() != ApprovalStepStatus.PENDING) {
            throw new ApprovalNotPendingException("Betalningen är redan behandlad. Ladda om sidan för att se det senaste.");
        }

        if (!(user != null && user.isAdmin()) && !Objects.equals(approvalStep.getAttestantId(), actorId)) {
            throw new ApprovalStepAccessDeniedException(
                    "Approval step " + approvalStepId + " is not assigned to actor " + actorId);
        }

        boolean earlierStepPending = payment.getApprovalSteps().stream()
                .anyMatch(step -> step.getStepNumber() < approvalStep.getStepNumber()
                        && step.getStatus() == ApprovalStepStatus.PENDING);

        if (earlierStepPending) {
            throw new ApprovalNotPendingException("Ett tidigare atteststeg måste godkännas först.");
        }

        approvalStep.setStatus(ApprovalStepStatus.APPROVED);
        approvalStep.setDecidedAt(LocalDateTime.now());
        approvalStep.setComment(comment);

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
            payment.recordAccountDebit(account.getBalance());
            account.setBalance(payment.getBalanceAfterPayment());
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExecutedAt(LocalDateTime.now());

            String description = withComment("Betalning godkänd: %s %s till %s".formatted(
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getToIban()), comment);

            auditService.record(
                    payment.getTenantId(),
                    actorId,
                    "APPROVE_PAYMENT",
                    "PAYMENT",
                    payment.getId(),
                    description,
                    comment
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

    private void rejectInternal(Long approvalStepId, Long actorId, String rawComment, AuthenticatedUserContext user) {
        String comment = normalizeComment(rawComment);

        if (approvalStepId == null) {
            throw new IllegalArgumentException("Approval step id is required");
        }

        if (actorId == null) {
            throw new IllegalArgumentException("Actor id is required");
        }

        Payment payment = findPaymentOfStepForUpdate(approvalStepId, user);

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new ApprovalNotPendingException("Betalningen är redan behandlad. Ladda om sidan för att se det senaste.");
        }

        ApprovalStep approvalStep = payment.getApprovalSteps().stream()
                .filter(step -> Objects.equals(step.getId(), approvalStepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Approval step not found: " + approvalStepId));

        if (approvalStep.getStatus() != ApprovalStepStatus.PENDING) {
            throw new ApprovalNotPendingException("Betalningen är redan behandlad. Ladda om sidan för att se det senaste.");
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

        String description = withComment("Betalning avvisad: %s %s till %s".formatted(
                payment.getAmount(),
                payment.getCurrency(),
                payment.getToIban()), comment);

        auditService.record(
                payment.getTenantId(),
                actorId,
                "REJECT_PAYMENT",
                "PAYMENT",
                payment.getId(),
                description,
                comment
        );
    }

    // Attestantens meddelande är frivilligt: tomt blir inget meddelande. Det kontrolleras innan betalningen
    // låses, så ett för långt meddelande ger 400 utan att något har ändrats.
    private static String normalizeComment(String rawComment) {
        String comment = rawComment == null || rawComment.isBlank() ? null : rawComment.trim();
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Kommentaren får vara högst " + MAX_COMMENT_LENGTH + " tecken.");
        }
        return comment;
    }

    // Meddelandet står också i auditradens beskrivning, eftersom den signerade loggen bara får beskrivningen
    // och meddelandet då omfattas av manipulationsskyddet.
    private static String withComment(String description, String comment) {
        return comment == null ? description : description + ". Kommentar: " + comment;
    }

    // Betalningen bakom steget, låst tills transaktionen är klar. Ett annat företags steg ser ut precis
    // som ett steg som inte finns (R-04): företaget kontrolleras före status, så att svaret inte avslöjar
    // om ett steg-id finns hos en annan kund eller om det redan är behandlat. user är null bara för de
    // interna anropen approve/reject(stepId, actorId), som inte har någon inloggad användare.
    private Payment findPaymentOfStepForUpdate(Long approvalStepId, AuthenticatedUserContext user) {
        return paymentRepository.findByApprovalStepIdForUpdate(approvalStepId)
                .filter(payment -> user == null
                        || (user.tenantId() != null && Objects.equals(payment.getTenantId(), user.tenantId())))
                .orElseThrow(() -> new IllegalArgumentException("Approval step not found: " + approvalStepId));
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
