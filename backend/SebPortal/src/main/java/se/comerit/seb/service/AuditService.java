package se.comerit.seb.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.AuditEntryResponse;
import se.comerit.seb.dto.MyPaymentStatusResponse;
import se.comerit.seb.dto.PaymentAuditTimelineEntryResponse;
import se.comerit.seb.infrastructure.audit.AuditSignerService;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.AuditRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private static final String SYSTEM_USER_NAME = "Systemet";
    private static final List<String> ATTESTANT_VISIBLE_ACTIONS = List.of(
            "CREATE_PAYMENT",
            "APPROVE_PAYMENT",
            "REJECT_PAYMENT"
    );

    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final AuditSignerService auditSigner;
    private final AccountRepository accountRepository;

    public AuditService(AuditRepository auditRepository,
                        UserRepository userRepository,
                        PaymentRepository paymentRepository,
                        AccountRepository accountRepository,
                        AuditSignerService auditSigner) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.auditSigner = auditSigner;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry record(Long tenantId, Long userId, String action,
                             String entityType, Long entityId, String description) {
        return record(tenantId, userId, action, entityType, entityId, description, null);
    }

    // comment: attestantens meddelande vid godkännande eller avvisning (#228). MANDATORY gör att raden
    // bara kan skrivas inuti beslutets transaktion, så meddelandet sparas med beslutet eller inte alls.
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry record(Long tenantId, Long userId, String action,
                             String entityType, Long entityId, String description, String comment) {
        AuditEntry entry = new AuditEntry(
                tenantId,
                userId,
                action,
                entityType,
                entityId,
                description,
                comment
        );

        AuditEntry saved = auditRepository.save(entry);
        signAfterCommit(userId, action, entityId, description);
        return saved;
    }

    // Skriv den signerade raden först när databastransaktionen har committats, annars hamnar en
    // händelse som rullades tillbaka i den signerade loggen. Signeringsfel stoppar aldrig affärsflödet.
    private void signAfterCommit(Long userId, String action, Long entityId, String description) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditSigner.append(userId, action, entityId, description);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditSigner.append(userId, action, entityId, description);
            }
        });
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> getAuditEntries(AuthenticatedUserContext user) {
        List<AuditEntry> entries = user.isAdmin()
                ? auditRepository.findTop200ByTenantIdOrderByCreatedAtDesc(user.tenantId())
                : auditRepository.findTop200ByTenantIdAndActionInOrderByCreatedAtDesc(
                        user.tenantId(),
                        ATTESTANT_VISIBLE_ACTIONS);

        List<Long> userIds = entries.stream()
                .map(AuditEntry::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> userNamesById = userNamesInTenant(user.tenantId(), userIds);

        List<Long> paymentIds = entries.stream()
                .filter(entry -> "PAYMENT".equalsIgnoreCase(entry.getEntityType()))
                .map(AuditEntry::getEntityId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        // Ingen fråga med en tom IN-lista
        Map<Long, Payment> paymentsById = paymentIds.isEmpty()
                ? Map.of()
                : paymentRepository.findByTenantIdAndIdInWithApprovalSteps(user.tenantId(), paymentIds).stream()
                        .collect(Collectors.toMap(Payment::getId, payment -> payment));

        Map<Long, String> accountNamesById = accountNamesForTenant(user.tenantId());

        return entries.stream()
                .map(entry -> {
                    Optional<Payment> payment = Optional.ofNullable(paymentsById.get(entry.getEntityId()));

                    return AuditEntryResponse.from(
                            entry,
                            userNamesById.getOrDefault(entry.getUserId(), SYSTEM_USER_NAME),
                            payment.map(p -> p.getStatus().name()).orElse(null),
                            payment.map(Payment::getReference).orElse(null),
                            payment.map(Payment::getAmount).orElse(null),
                            payment.map(Payment::getCurrency).orElse(null),
                            payment.map(Payment::getBalanceBeforePayment).orElse(null),
                            payment.map(Payment::getBalanceAfterPayment).orElse(null),
                            payment.map(p -> accountNamesById.get(p.getFromAccountId())).orElse(null),
                            payment.map(AuditService::isAutoApproved).orElse(false));
                })
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<PaymentAuditTimelineEntryResponse> getPaymentAuditTimeline(
            AuthenticatedUserContext user,
            Long paymentId
    ) {
        Payment payment = paymentRepository.findByIdAndTenantIdWithApprovalSteps(paymentId, user.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        List<AuditEntry> auditEntries = auditRepository.findPaymentEvents(user.tenantId(), paymentId);

        if (!user.isAdmin()) {
            auditEntries = auditEntries.stream()
                    .filter(entry -> ATTESTANT_VISIBLE_ACTIONS.contains(entry.getAction()))
                    .collect(Collectors.toList());
        }

        Set<Long> userIds = auditEntries.stream()
                .map(AuditEntry::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        if (user.isAdmin()) {
            payment.getApprovalSteps().stream()
                    .map(ApprovalStep::getAttestantId)
                    .filter(id -> id != null)
                    .forEach(userIds::add);
        }

        Map<Long, String> userNamesById = userNamesInTenant(user.tenantId(), userIds);

        List<TimelineEvent> events = new ArrayList<>();

        for (AuditEntry entry : auditEntries) {
            events.add(new TimelineEvent(
                    entry.getCreatedAt(),
                    1,
                    entry.getId(),
                    "AUDIT-" + entry.getId(),
                    userNamesById.getOrDefault(entry.getUserId(), SYSTEM_USER_NAME),
                    entry.getAction(),
                    entry.getDescription(),
                    null
            ));
        }

        if (user.isAdmin()) {
            for (ApprovalStep step : payment.getApprovalSteps()) {
                LocalDateTime timestamp = step.getDecidedAt();
                String eventType = "APPROVAL_STEP_" + step.getStatus().name();
                String description = "Atteststeg %d är %s".formatted(
                        step.getStepNumber(),
                        step.getStatus().name());

                events.add(new TimelineEvent(
                        timestamp,
                        2,
                        step.getId(),
                        "APPROVAL_STEP-" + step.getId(),
                        userNamesById.getOrDefault(step.getAttestantId(), SYSTEM_USER_NAME),
                        eventType,
                        description,
                        step.getStepNumber()
                ));
            }
        }

        events.sort(Comparator
                .comparing(TimelineEvent::timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TimelineEvent::sourceRank)
                .thenComparing(TimelineEvent::sourceId));

        List<PaymentAuditTimelineEntryResponse> response = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            TimelineEvent event = events.get(index);
            response.add(new PaymentAuditTimelineEntryResponse(
                    index + 1,
                    event.sequence(),
                    event.actor(),
                    event.eventType(),
                    event.description(),
                    event.timestamp(),
                    event.stepNumber(),
                    payment.getStatus().name(),
                    payment.getReference(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getToIban()
            ));
        }

        return response;
    }

    @PreAuthorize("hasAnyRole('INITIATOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<MyPaymentStatusResponse> getMyPaymentStatuses(AuthenticatedUserContext user) {
        List<Payment> payments = paymentRepository
                .findByTenantIdAndCreatedByWithApprovalSteps(user.tenantId(), user.userId());

        List<Long> attestantIds = payments.stream()
                .flatMap(payment -> payment.getApprovalSteps().stream())
                .map(ApprovalStep::getAttestantId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        // Attestanterna tillhör samma företag som betalningen; namnen slås upp en gång för hela listan
        Map<Long, String> attestantNamesById = userNamesInTenant(user.tenantId(), attestantIds);

        Map<Long, String> accountNamesById = accountNamesForTenant(user.tenantId());

        return payments.stream()
                .map(payment -> MyPaymentStatusResponse.from(payment, accountNamesById, attestantNamesById))
                .collect(Collectors.toList());
    }

    // Bara det egna företagets användare namnges (R-04). En auditrad eller ett steg som pekar på en
    // användare hos ett annat företag (bara möjligt med felaktig data) visas som en okänd användare.
    private Map<Long, String> userNamesInTenant(Long tenantId, Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                .collect(Collectors.toMap(User::getId, User::getName));
    }

    // Bara det egna företagets konton slås upp, så ett annat företags konto kan aldrig namnges (R-04)
    private Map<Long, String> accountNamesForTenant(Long tenantId) {
        return accountRepository.findByTenantIdOrderByIdAsc(tenantId).stream()
                .collect(Collectors.toMap(account -> account.getId().longValue(), Account::getAccountName));
    }

    // Genomförd utan atteststeg: beloppet låg under tröskeln, så betalningen godkändes automatiskt
    // när den skapades (PaymentService).
    private static boolean isAutoApproved(Payment payment) {
        return payment.getStatus() == PaymentStatus.COMPLETED && payment.getApprovalSteps().isEmpty();
    }

    private record TimelineEvent(
            LocalDateTime timestamp,
            int sourceRank,
            Long sourceId,
            String sequence,
            String actor,
            String eventType,
            String description,
            Integer stepNumber
    ) {}
}
