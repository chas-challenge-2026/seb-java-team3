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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ApprovalService {

    // Samma text som PaymentService använder vid skapande: saknat konto, ogiltigt id och ett annat
    // företags konto ska vara omöjliga att skilja åt, så att konto-id hos andra kunder inte
    // går att kartlägga.
    private static final String FROM_ACCOUNT_NOT_AVAILABLE =
            "Från-kontot finns inte eller tillhör inte ditt företag";

    // Samma text för ett steg som inte finns och ett annat företags steg, utan id:t i svaret (R-04, R-18).
    private static final String APPROVAL_STEP_NOT_FOUND = "Atteststeget hittades inte.";

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
    // Kontonamn och namnen på initiatören och tidigare attestanter slås upp en gång för hela listan, och
    // bara inom företaget, så att ett annat företags konton eller användare aldrig namnges (R-04).
    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<PendingApprovalResponse> getPendingApprovals(AuthenticatedUserContext user) {
        List<Payment> payments = user.isAdmin()
                ? paymentRepository.findPendingApprovalsForTenant(user.tenantId())
                : paymentRepository.findPendingApprovalsForAttestant(user.tenantId(), user.userId());

        Map<Long, Account> accountsById = accountRepository.findByTenantIdOrderByIdAsc(user.tenantId()).stream()
                .collect(Collectors.toMap(account -> account.getId().longValue(), account -> account));

        List<Long> userIds = payments.stream()
                .flatMap(payment -> Stream.concat(
                        Stream.of(payment.getCreatedBy()),
                        payment.getApprovalSteps().stream().map(ApprovalStep::getDecidedBy)))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> userNamesById = userRepository.findAllById(userIds).stream()
                .filter(candidate -> user.tenantId().equals(candidate.getTenantId()))
                .collect(Collectors.toMap(User::getId, User::getName));

        return payments.stream()
                .flatMap(payment -> stepUserCanDecide(payment, user).stream()
                        .map(step -> PendingApprovalResponse.from(
                                payment,
                                step,
                                accountsById.get(payment.getFromAccountId()),
                                userNamesById)))
                .toList();
    }

    // Det som visas i attestkorgen och räknas i badgen: steget som är på tur, om användaren får besluta om
    // det. Attest 2 syns alltså inte förrän attest 1 är klar, och en admin ser inte ett steg som hen ändå
    // skulle nekas att godkänna.
    private static Optional<ApprovalStep> stepUserCanDecide(Payment payment, AuthenticatedUserContext user) {
        return payment.getApprovalSteps().stream()
                .filter(step -> step.getStatus() == ApprovalStepStatus.PENDING)
                .min(Comparator.comparing(ApprovalStep::getStepNumber))
                .filter(step -> user.isAdmin() || user.userId().equals(step.getAttestantId()))
                .filter(step -> !breaksDualControl(payment, user.userId()));
    }

    // Dubbel attest ska vara två av varandra oberoende personer (ADR 0016): den som har skapat betalningen
    // eller redan godkänt ett av stegen får inte godkänna fler. Kontrollen behövs för admin, som får besluta
    // om alla företagets steg. En betalning med ett steg berörs inte.
    private static boolean breaksDualControl(Payment payment, Long actorId) {
        List<ApprovalStep> steps = payment.getApprovalSteps();
        if (steps.size() < 2) {
            return false;
        }
        return Objects.equals(payment.getCreatedBy(), actorId)
                || steps.stream().anyMatch(step -> step.getStatus() == ApprovalStepStatus.APPROVED
                        && Objects.equals(step.getDecidedBy(), actorId));
    }

    private static void requireNoEarlierStepPending(Payment payment, ApprovalStep approvalStep) {
        boolean earlierStepPending = payment.getApprovalSteps().stream()
                .anyMatch(step -> step.getStepNumber() < approvalStep.getStepNumber()
                        && step.getStatus() == ApprovalStepStatus.PENDING);

        if (earlierStepPending) {
            throw new ApprovalNotPendingException("Ett tidigare atteststeg måste godkännas först.");
        }
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

        Payment payment = findPaymentOfStepForUpdate(approvalStepId, user);

        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
            throw new ApprovalNotPendingException("Betalningen är redan behandlad. Ladda om sidan för att se det senaste.");
        }

        ApprovalStep approvalStep = payment.getApprovalSteps().stream()
                .filter(step -> Objects.equals(step.getId(), approvalStepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(APPROVAL_STEP_NOT_FOUND));

        if (approvalStep.getStatus() != ApprovalStepStatus.PENDING) {
            throw new ApprovalNotPendingException("Betalningen är redan behandlad. Ladda om sidan för att se det senaste.");
        }

        if (!(user != null && user.isAdmin()) && !Objects.equals(approvalStep.getAttestantId(), actorId)) {
            throw new ApprovalStepAccessDeniedException(
                    "Approval step " + approvalStepId + " is not assigned to actor " + actorId);
        }

        requireNoEarlierStepPending(payment, approvalStep);

        if (breaksDualControl(payment, actorId)) {
            throw new ApprovalStepAccessDeniedException(
                    "Actor " + actorId + " has created or already approved the payment of approval step " + approvalStepId);
        }

        approvalStep.setStatus(ApprovalStepStatus.APPROVED);
        approvalStep.setDecidedAt(LocalDateTime.now());
        approvalStep.setDecidedBy(actorId);

        boolean allApproved = payment.getApprovalSteps().stream()
                .allMatch(step -> step.getStatus() == ApprovalStepStatus.APPROVED);

        if (!allApproved) {
            // Varje godkännande ska gå att följa i loggen, även det som inte genomför betalningen.
            auditService.record(
                    payment.getTenantId(),
                    actorId,
                    "APPROVE_STEP",
                    "PAYMENT",
                    payment.getId(),
                    "Attest %d av %d godkänd: %s %s till %s".formatted(
                            approvalStep.getStepNumber(),
                            payment.getApprovalSteps().size(),
                            payment.getAmount(),
                            payment.getCurrency(),
                            payment.getToIban())
            );
        } else {
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

    // Samma gräns som kolumnen approval_steps.comment (V3), så att en för lång kommentar ger 400 i stället
    // för ett databasfel.
    static final int MAX_COMMENT_LENGTH = 255;

    // Kontrolltecken ger 400: Postgres tar inte emot NUL i text, och ett meddelande med bara kontrolltecken
    // hade blivit tomt efter trim. Radbrytningar och tabbar är vanlig fritext och får vara kvar.
    private static boolean containsControlCharacter(String text) {
        return text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }

    private void rejectInternal(Long approvalStepId, Long actorId, String rawComment, AuthenticatedUserContext user) {
        if (rawComment != null && containsControlCharacter(rawComment)) {
            throw new IllegalArgumentException("Kommentaren innehåller ogiltiga tecken.");
        }

        String comment = rawComment == null || rawComment.isBlank() ? null : rawComment.trim();
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Kommentaren får vara högst " + MAX_COMMENT_LENGTH + " tecken.");
        }

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
                .orElseThrow(() -> new IllegalArgumentException(APPROVAL_STEP_NOT_FOUND));

        if (approvalStep.getStatus() != ApprovalStepStatus.PENDING) {
            throw new ApprovalNotPendingException("Betalningen är redan behandlad. Ladda om sidan för att se det senaste.");
        }

        if (!(user != null && user.isAdmin()) && !Objects.equals(approvalStep.getAttestantId(), actorId)) {
            throw new ApprovalStepAccessDeniedException(
                    "Approval step " + approvalStepId + " is not assigned to actor " + actorId);
        }

        requireNoEarlierStepPending(payment, approvalStep);

        approvalStep.setStatus(ApprovalStepStatus.REJECTED);
        approvalStep.setDecidedAt(LocalDateTime.now());
        approvalStep.setDecidedBy(actorId);
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
        // Meddelandet står också i beskrivningen, eftersom den signerade loggen bara får beskrivningen
        if (comment != null) {
            description += ". Kommentar: " + comment;
        }

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

    // Betalningen bakom steget, låst tills transaktionen är klar. Ett annat företags steg ser ut precis
    // som ett steg som inte finns (R-04): företaget kontrolleras före status, så att svaret inte avslöjar
    // om ett steg-id finns hos en annan kund eller om det redan är behandlat. user är null bara för de
    // interna anropen approve/reject(stepId, actorId), som inte har någon inloggad användare.
    private Payment findPaymentOfStepForUpdate(Long approvalStepId, AuthenticatedUserContext user) {
        return paymentRepository.findByApprovalStepIdForUpdate(approvalStepId)
                .filter(payment -> user == null
                        || (user.tenantId() != null && Objects.equals(payment.getTenantId(), user.tenantId())))
                .orElseThrow(() -> new IllegalArgumentException(APPROVAL_STEP_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public int countPendingByAttestant(AuthenticatedUserContext user) {
        var payments = user.isAdmin()
                ? paymentRepository.findPendingApprovalsForTenant(user.tenantId())
                : paymentRepository.findPendingApprovalsForAttestant(user.tenantId(), user.userId());
        return (int) payments
                .stream()
                .filter(payment -> stepUserCanDecide(payment, user).isPresent())
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
