package se.comerit.seb.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

// #230: initiatorns badge räknar bara beslut som fattats efter att användaren senast markerade
// sina betalningar som sedda. Servicetesterna mockar PaymentRepository och kan därför inte visa
// att själva frågan (JOIN på atteststegen och jämförelsen mot seenAt) räknar rätt. Det här testet
// kör frågan mot en riktig SQL-motor (H2 i minnet), på samma sätt som AccountRepositoryTenantTest.
// Kontrollfallet (en betalning som ska räknas) står först i varje test: utan det skulle de negativa
// fallen kunna vara gröna bara för att tabellen är tom.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentRepositoryChangedCountTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long INITIATOR = 10L;
    private static final Long OTHER_INITIATOR = 11L;
    private static final Long ATTESTANT = 20L;

    // Samma värde som PaymentService.NEVER_SEEN: användaren har aldrig markerat något som sett.
    private static final LocalDateTime NEVER_SEEN = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime SEEN_AT = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Autowired
    private PaymentRepository paymentRepository;

    // Hjälpmetod: en betalning med ett atteststeg per angiven beslutstid. Utan tider får betalningen
    // inga steg alls, som en betalning under tröskeln.
    private Payment savePayment(Long tenantId, Long createdBy, PaymentStatus status,
                                ApprovalStepStatus stepStatus, LocalDateTime... decidedAt) {
        Payment payment = new Payment(
                tenantId, 1L, "SE4550000000058398257466", new BigDecimal("6000.00"), "Test", createdBy);
        payment.setStatus(status);
        int stepNumber = 1;
        for (LocalDateTime time : decidedAt) {
            ApprovalStep step = new ApprovalStep(ATTESTANT, stepNumber++);
            step.setStatus(stepStatus);
            step.setDecidedAt(time);
            payment.addApprovalStep(step);
        }
        return paymentRepository.save(payment);
    }

    @Test
    void countsDecisionsMadeAfterSeenAt() {
        // Kontrollfall: ett godkänt och ett avvisat beslut efter seenAt räknas
        savePayment(TENANT_A, INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.plusMinutes(5));
        savePayment(TENANT_A, INITIATOR, PaymentStatus.REJECTED,
                ApprovalStepStatus.REJECTED, SEEN_AT.plusMinutes(10));

        assertEquals(2, paymentRepository.countDecidedByInitiator(TENANT_A, INITIATOR, SEEN_AT));
    }

    @Test
    void ignoresDecisionsMadeBeforeOrAtSeenAt() {
        // Kontrollfall: ett beslut efter seenAt räknas
        savePayment(TENANT_A, INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.plusMinutes(1));

        // Beslut före, och exakt vid, seenAt är redan sedda (jämförelsen är strikt "efter")
        savePayment(TENANT_A, INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.minusMinutes(5));
        savePayment(TENANT_A, INITIATOR, PaymentStatus.REJECTED,
                ApprovalStepStatus.REJECTED, SEEN_AT);

        assertEquals(1, paymentRepository.countDecidedByInitiator(TENANT_A, INITIATOR, SEEN_AT));
    }

    @Test
    void countsEveryDecisionWhenTheUserHasNeverMarkedAnythingAsSeen() {
        savePayment(TENANT_A, INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.minusYears(1));
        savePayment(TENANT_A, INITIATOR, PaymentStatus.REJECTED,
                ApprovalStepStatus.REJECTED, SEEN_AT.plusMinutes(5));

        assertEquals(2, paymentRepository.countDecidedByInitiator(TENANT_A, INITIATOR, NEVER_SEEN));
    }

    @Test
    void ignoresPaymentsWithoutApprovalSteps() {
        // Kontrollfall: en betalning med beslutat steg räknas
        savePayment(TENANT_A, INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.plusMinutes(5));

        // Under tröskeln blir betalningen COMPLETED direkt utan steg: ingen har fattat något beslut
        savePayment(TENANT_A, INITIATOR, PaymentStatus.COMPLETED, ApprovalStepStatus.APPROVED);

        assertEquals(1, paymentRepository.countDecidedByInitiator(TENANT_A, INITIATOR, NEVER_SEEN));
    }

    @Test
    void ignoresPaymentsStillWaitingForApproval() {
        // Kontrollfall
        savePayment(TENANT_A, INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.plusMinutes(5));

        // Väntande: steget har ingen beslutstid och betalningen är inte COMPLETED/REJECTED
        savePayment(TENANT_A, INITIATOR, PaymentStatus.PENDING_APPROVAL,
                ApprovalStepStatus.PENDING, (LocalDateTime) null);

        assertEquals(1, paymentRepository.countDecidedByInitiator(TENANT_A, INITIATOR, NEVER_SEEN));
    }

    @Test
    void countsAPaymentWithSeveralDecidedStepsOnlyOnce() {
        // Vid avvisning sätts beslutstid på alla steg i kedjan. Betalningen ska ändå bara räknas en gång.
        savePayment(TENANT_A, INITIATOR, PaymentStatus.REJECTED, ApprovalStepStatus.REJECTED,
                SEEN_AT.plusMinutes(5), SEEN_AT.plusMinutes(5));

        assertEquals(1, paymentRepository.countDecidedByInitiator(TENANT_A, INITIATOR, SEEN_AT));
    }

    @Test
    void countsOnlyTheInitiatorsOwnPaymentsWithinTheTenant() {
        savePayment(TENANT_A, INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.plusMinutes(5));
        savePayment(TENANT_A, OTHER_INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.plusMinutes(5));
        // Samma användar-id i ett annat företag får aldrig synas
        savePayment(TENANT_B, INITIATOR, PaymentStatus.COMPLETED,
                ApprovalStepStatus.APPROVED, SEEN_AT.plusMinutes(5));

        assertEquals(1, paymentRepository.countDecidedByInitiator(TENANT_A, INITIATOR, SEEN_AT));
        assertEquals(1, paymentRepository.countDecidedByInitiator(TENANT_A, OTHER_INITIATOR, SEEN_AT));
        assertEquals(1, paymentRepository.countDecidedByInitiator(TENANT_B, INITIATOR, SEEN_AT));
    }
}