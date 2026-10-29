package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Avvisa från attestkorgen: attestantens kommentar sparas på steget och på auditraden, både i beskrivningen
// och i en egen kolumn (#228).
// Kolumnen rymmer 255 tecken, så en längre kommentar ska ge 400 och inte ett databasfel.
class ApprovalServiceRejectCommentTest {

    private static final Long TENANT = 1L;
    private static final Long ATTESTANT_ID = 2L;
    private static final Long STEP_ID = 200L;

    private PaymentRepository paymentRepository;
    private AuditService auditService;
    private ApprovalService approvalService;
    private Payment payment;
    private ApprovalStep step;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        auditService = mock(AuditService.class);
        approvalService = new ApprovalService(paymentRepository, mock(AccountRepository.class), auditService,
                mock(UserRepository.class));

        payment = new Payment(TENANT, 10L, "SE8550000000054910000003", new BigDecimal("270000.00"), "B2", 5L);
        step = new ApprovalStep(ATTESTANT_ID, 1);
        payment.addApprovalStep(step);
        ReflectionTestUtils.setField(payment, "id", 100L);
        ReflectionTestUtils.setField(step, "id", STEP_ID);
        when(paymentRepository.findByApprovalStepIdForUpdate(STEP_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    void comment_isSavedTrimmedAndWrittenToTheAuditLog() {
        approvalService.reject(STEP_ID, ATTESTANT_ID, "  Otillräckligt saldo  ");

        assertEquals("Otillräckligt saldo", step.getComment());
        assertEquals(PaymentStatus.REJECTED, payment.getStatus());
        verify(auditService).record(eq(TENANT), eq(ATTESTANT_ID), eq("REJECT_PAYMENT"), eq("PAYMENT"), eq(100L),
                eq("Betalning avvisad: 270000.00 SEK till SE8550000000054910000003. Kommentar: Otillräckligt saldo"),
                eq("Otillräckligt saldo"));
    }

    @Test
    void blankComment_isSavedAsNoComment() {
        approvalService.reject(STEP_ID, ATTESTANT_ID, "   ");

        assertNull(step.getComment());
        verify(auditService).record(eq(TENANT), eq(ATTESTANT_ID), eq("REJECT_PAYMENT"), eq("PAYMENT"), eq(100L),
                eq("Betalning avvisad: 270000.00 SEK till SE8550000000054910000003"), isNull());
    }

    @Test
    void commentOfExactlyTheMaximumLength_isAccepted() {
        String comment = "x".repeat(ApprovalService.MAX_COMMENT_LENGTH);

        approvalService.reject(STEP_ID, ATTESTANT_ID, comment);

        assertEquals(comment, step.getComment());
    }

    @Test
    void tooLongComment_isRejectedWithoutChangingAnything() {
        String comment = "x".repeat(ApprovalService.MAX_COMMENT_LENGTH + 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> approvalService.reject(STEP_ID, ATTESTANT_ID, comment));

        assertEquals("Kommentaren får vara högst 255 tecken.", error.getMessage());
        assertEquals(ApprovalStepStatus.PENDING, step.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        assertNull(step.getComment());
        verifyNoInteractions(auditService);
    }

    @Test
    void rejection_doesNotTouchAnyAccount() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        ApprovalService service = new ApprovalService(paymentRepository, accountRepository, auditService,
                mock(UserRepository.class));

        service.reject(STEP_ID, ATTESTANT_ID, "Fel mottagare");

        // Avvisning drar inga pengar; reservationen släpps av sig själv när betalningen inte längre väntar
        verifyNoInteractions(accountRepository);
        verify(auditService).record(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(),
                eq("Fel mottagare"));
    }
}
