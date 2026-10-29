package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.PaymentAuditTimelineEntryResponse;
import se.comerit.seb.infrastructure.audit.AuditSignerService;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.AuditRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Händelsekedjan för en betalning, som visas när en rad i Historik fälls ut. #228: attestantens meddelande
// följer med på händelsen där beslutet fattades.
class AuditServiceTimelineTest {

    private static final Long TENANT = 1L;
    private static final Long INITIATOR_ID = 10L;
    private static final Long ATTESTANT_ID = 20L;
    private static final Long PAYMENT_ID = 100L;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 29, 9, 0);
    private static final LocalDateTime REJECTED = CREATED.plusHours(1);

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        AuditRepository auditRepository = mock(AuditRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        auditService = new AuditService(auditRepository, mock(UserRepository.class), paymentRepository,
                mock(AccountRepository.class), mock(AuditSignerService.class));

        Payment payment = new Payment(TENANT, 1L, "SE4550000000058398257466",
                new BigDecimal("6000.00"), "Demo", INITIATOR_ID);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.setStatus(PaymentStatus.REJECTED);
        ApprovalStep step = new ApprovalStep(ATTESTANT_ID, 1);
        ReflectionTestUtils.setField(step, "id", 300L);
        step.setStatus(ApprovalStepStatus.REJECTED);
        step.setDecidedAt(REJECTED);
        step.setComment("Fel konto");
        payment.addApprovalStep(step);

        AuditEntry created = auditEntry(1L, CREATED, INITIATOR_ID, "CREATE_PAYMENT", "Betalning skapad", null);
        AuditEntry rejected = auditEntry(2L, REJECTED, ATTESTANT_ID, "REJECT_PAYMENT",
                "Betalning avvisad. Kommentar: Fel konto", "Fel konto");

        when(paymentRepository.findByIdAndTenantIdWithApprovalSteps(PAYMENT_ID, TENANT))
                .thenReturn(Optional.of(payment));
        when(auditRepository.findPaymentEvents(TENANT, PAYMENT_ID)).thenReturn(List.of(created, rejected));
    }

    @Test
    void attestant_seesTheMessageOnTheRejection() {
        List<PaymentAuditTimelineEntryResponse> timeline = auditService.getPaymentAuditTimeline(
                new AuthenticatedUserContext(ATTESTANT_ID, TENANT, Role.ATTESTANT), PAYMENT_ID);

        assertEquals(2, timeline.size());
        assertEquals("Fel konto", event(timeline, "REJECT_PAYMENT").comment());
        assertNull(event(timeline, "CREATE_PAYMENT").comment());
    }

    // En admin ser också atteststegen. Steget som avvisade bär samma meddelande, så att det inte tappas om
    // en betalning med flera steg avvisas eller godkänns i ett steg som inte skriver någon auditrad.
    @Test
    void admin_seesTheMessageOnTheStepThatDecided() {
        List<PaymentAuditTimelineEntryResponse> timeline = auditService.getPaymentAuditTimeline(
                new AuthenticatedUserContext(1L, TENANT, Role.ADMIN), PAYMENT_ID);

        assertEquals(3, timeline.size());
        assertEquals("Fel konto", event(timeline, "REJECT_PAYMENT").comment());
        assertEquals("Fel konto", event(timeline, "APPROVAL_STEP_REJECTED").comment());
    }

    private static PaymentAuditTimelineEntryResponse event(List<PaymentAuditTimelineEntryResponse> timeline,
                                                           String eventType) {
        return timeline.stream()
                .filter(entry -> eventType.equals(entry.eventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ingen händelse " + eventType + " i " + timeline));
    }

    private static AuditEntry auditEntry(Long id, LocalDateTime createdAt, Long userId, String action,
                                         String description, String comment) {
        AuditEntry entry = new AuditEntry(TENANT, userId, action, "PAYMENT", PAYMENT_ID, description, comment);
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "createdAt", createdAt);
        return entry;
    }
}
