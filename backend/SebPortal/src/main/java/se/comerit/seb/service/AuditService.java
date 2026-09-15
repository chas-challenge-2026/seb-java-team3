package se.comerit.seb.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.AuditEntryResponse;
import se.comerit.seb.dto.PaymentAuditTimelineEntryResponse;
import se.comerit.seb.repository.AuditRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public AuditService(AuditRepository auditRepository,
                        UserRepository userRepository,
                        PaymentRepository paymentRepository) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry record(Long tenantId, Long userId, String action,
                             String entityType, Long entityId, String description) {
        AuditEntry entry = new AuditEntry(
                tenantId,
                userId,
                action,
                entityType,
                entityId,
                description
        );

        return auditRepository.save(entry);
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

        Map<Long, String> userNamesById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        List<Long> paymentIds = entries.stream()
                .filter(entry -> "PAYMENT".equalsIgnoreCase(entry.getEntityType()))
                .map(AuditEntry::getEntityId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Payment> paymentsById = paymentRepository.findAllById(paymentIds).stream()
                .collect(Collectors.toMap(Payment::getId, payment -> payment));

        return entries.stream()
                .map(entry -> {
                    Optional<Payment> payment = Optional.ofNullable(paymentsById.get(entry.getEntityId()));

                    return AuditEntryResponse.from(
                            entry,
                            userNamesById.getOrDefault(entry.getUserId(), SYSTEM_USER_NAME),
                            payment.map(p -> p.getStatus().name()).orElse(null),
                            payment.map(Payment::getReference).orElse(null),
                            payment.map(Payment::getAmount).orElse(null),
                            payment.map(Payment::getCurrency).orElse(null));
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

        Map<Long, String> userNamesById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

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
