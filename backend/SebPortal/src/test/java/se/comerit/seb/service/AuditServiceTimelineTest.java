package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.PaymentAuditTimelineEntryResponse;
import se.comerit.seb.repository.AuditRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// #196: ordningen i en betalnings tidslinje (getPaymentAuditTimeline). Repositoryt är mockat
// och lämnar med flit ut händelserna i FEL ordning, så att det är sorteringen i servicen som
// testas, inte ORDER BY i frågan (den testas i AuditRepositoryPaymentEventsTest).
class AuditServiceTimelineTest {

    private static final Long TENANT_ID = 1L;
    private static final Long PAYMENT_ID = 100L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 8, 9, 0);

    private final AuthenticatedUserContext attestant = new AuthenticatedUserContext(2L, TENANT_ID, Role.ATTESTANT);
    private final AuthenticatedUserContext admin = new AuthenticatedUserContext(3L, TENANT_ID, Role.ADMIN);

    private AuditRepository auditRepository;
    private PaymentRepository paymentRepository;
    private AuditService auditService;
    private Payment payment;

    @BeforeEach
    void setUp() {
        auditRepository = mock(AuditRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        auditService = new AuditService(auditRepository, userRepository, paymentRepository);

        payment = new Payment(TENANT_ID, 10L, "SE8550000000054910000003",
                new BigDecimal("200.00"), "Testfaktura", 1L);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);

        when(paymentRepository.findByIdAndTenantIdWithApprovalSteps(PAYMENT_ID, TENANT_ID))
                .thenReturn(Optional.of(payment));
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void timeline_isChronological_createBeforeApprove() {
        AuditEntry create = auditEntry(1L, "CREATE_PAYMENT", T0);
        AuditEntry approve = auditEntry(2L, "APPROVE_PAYMENT", T0.plusMinutes(5));
        when(auditRepository.findPaymentEvents(TENANT_ID, PAYMENT_ID)).thenReturn(List.of(approve, create));

        List<PaymentAuditTimelineEntryResponse> timeline = auditService.getPaymentAuditTimeline(attestant, PAYMENT_ID);

        assertEquals(List.of("CREATE_PAYMENT", "APPROVE_PAYMENT"), eventTypes(timeline));
        assertEquals(List.of(1, 2), timeline.stream().map(PaymentAuditTimelineEntryResponse::order).toList());
    }

    @Test
    void timeline_withSameTimestamp_isOrderedBySequence_regardlessOfInputOrder() {
        // id 9 och 10 med flit: sorteras sekvensen som text hamnar "AUDIT-10" före "AUDIT-9".
        // Tie-breaken ska vara numerisk, alltså 9 före 10.
        AuditEntry first = auditEntry(9L, "CREATE_PAYMENT", T0);
        AuditEntry second = auditEntry(10L, "APPROVE_PAYMENT", T0);

        when(auditRepository.findPaymentEvents(TENANT_ID, PAYMENT_ID)).thenReturn(List.of(second, first));
        List<String> fromReversedInput = sequences(auditService.getPaymentAuditTimeline(attestant, PAYMENT_ID));

        when(auditRepository.findPaymentEvents(TENANT_ID, PAYMENT_ID)).thenReturn(List.of(first, second));
        List<String> fromOrderedInput = sequences(auditService.getPaymentAuditTimeline(attestant, PAYMENT_ID));

        assertEquals(List.of("AUDIT-9", "AUDIT-10"), fromReversedInput);
        assertEquals(fromReversedInput, fromOrderedInput);
    }

    @Test
    void timeline_forAdmin_putsTheAuditEntryBeforeTheApprovalStepWithTheSameTimestamp() {
        // Admin ser även atteststegen. Godkännandet loggas och steget avgörs i samma
        // transaktion, så de kan få exakt samma tidsstämpel.
        ApprovalStep step = new ApprovalStep(2L, 1);
        ReflectionTestUtils.setField(step, "id", 1L);
        step.setStatus(ApprovalStepStatus.APPROVED);
        step.setDecidedAt(T0.plusMinutes(5));
        payment.addApprovalStep(step);

        AuditEntry create = auditEntry(1L, "CREATE_PAYMENT", T0);
        AuditEntry approve = auditEntry(2L, "APPROVE_PAYMENT", T0.plusMinutes(5));
        when(auditRepository.findPaymentEvents(TENANT_ID, PAYMENT_ID)).thenReturn(List.of(approve, create));

        List<PaymentAuditTimelineEntryResponse> timeline = auditService.getPaymentAuditTimeline(admin, PAYMENT_ID);

        assertEquals(List.of("CREATE_PAYMENT", "APPROVE_PAYMENT", "APPROVAL_STEP_APPROVED"), eventTypes(timeline));
    }

    private AuditEntry auditEntry(Long id, String action, LocalDateTime createdAt) {
        AuditEntry entry = new AuditEntry(TENANT_ID, 1L, action, "PAYMENT", PAYMENT_ID, action);
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "createdAt", createdAt);
        return entry;
    }

    private static List<String> eventTypes(List<PaymentAuditTimelineEntryResponse> timeline) {
        return timeline.stream().map(PaymentAuditTimelineEntryResponse::eventType).toList();
    }

    private static List<String> sequences(List<PaymentAuditTimelineEntryResponse> timeline) {
        return timeline.stream().map(PaymentAuditTimelineEntryResponse::sequence).toList();
    }
}
