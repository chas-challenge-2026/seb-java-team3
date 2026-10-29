package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.domain.Account;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Godkänn från attestkorgen (#228): samma frivilliga meddelande som vid avvisning. Det sparas på steget och
// på auditraden (i beskrivningen och i en egen kolumn), och ett för långt meddelande stoppas innan något
// låses eller dras.
class ApprovalServiceApproveCommentTest {

    private static final Long TENANT = 1L;
    private static final Long ATTESTANT_ID = 2L;
    private static final Long STEP_ID = 200L;
    private static final Long ACCOUNT_ID = 10L;

    private PaymentRepository paymentRepository;
    private AccountRepository accountRepository;
    private AuditService auditService;
    private ApprovalService approvalService;
    private Payment payment;
    private ApprovalStep step;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        accountRepository = mock(AccountRepository.class);
        auditService = mock(AuditService.class);
        approvalService = new ApprovalService(paymentRepository, accountRepository, auditService,
                mock(UserRepository.class));

        payment = new Payment(TENANT, ACCOUNT_ID, "SE8550000000054910000003", new BigDecimal("270.00"), "B2", 5L);
        step = new ApprovalStep(ATTESTANT_ID, 1);
        payment.addApprovalStep(step);
        ReflectionTestUtils.setField(payment, "id", 100L);
        ReflectionTestUtils.setField(step, "id", STEP_ID);
        when(paymentRepository.findByApprovalStepIdForUpdate(STEP_ID)).thenReturn(Optional.of(payment));

        Account account = mock(Account.class);
        when(account.getBalance()).thenReturn(new BigDecimal("1000.00"));
        when(accountRepository.findByIdAndTenantIdForUpdate(ACCOUNT_ID.intValue(), TENANT))
                .thenReturn(Optional.of(account));
    }

    @Test
    void comment_isSavedTrimmedAndWrittenToTheAuditLog() {
        approvalService.approve(STEP_ID, ATTESTANT_ID, "  Stämmer mot faktura 4711  ");

        assertEquals("Stämmer mot faktura 4711", step.getComment());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        verify(auditService).record(eq(TENANT), eq(ATTESTANT_ID), eq("APPROVE_PAYMENT"), eq("PAYMENT"), eq(100L),
                eq("Betalning godkänd: 270.00 SEK till SE8550000000054910000003. Kommentar: Stämmer mot faktura 4711"),
                eq("Stämmer mot faktura 4711"));
    }

    // Attestkorgen skickar i dag ingen body vid godkännande, och det ska fortsätta fungera
    @Test
    void noComment_approvesWithoutAMessage() {
        approvalService.approve(STEP_ID, ATTESTANT_ID, null);

        assertNull(step.getComment());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        verify(auditService).record(eq(TENANT), eq(ATTESTANT_ID), eq("APPROVE_PAYMENT"), eq("PAYMENT"), eq(100L),
                eq("Betalning godkänd: 270.00 SEK till SE8550000000054910000003"), isNull());
    }

    @Test
    void blankComment_isSavedAsNoComment() {
        approvalService.approve(STEP_ID, ATTESTANT_ID, "   ");

        assertNull(step.getComment());
    }

    @Test
    void tooLongComment_isRejectedBeforeAnyMoneyMoves() {
        String comment = "x".repeat(ApprovalService.MAX_COMMENT_LENGTH + 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> approvalService.approve(STEP_ID, ATTESTANT_ID, comment));

        assertEquals("Kommentaren får vara högst 255 tecken.", error.getMessage());
        assertEquals(ApprovalStepStatus.PENDING, step.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        assertNull(step.getComment());
        verifyNoInteractions(paymentRepository, accountRepository, auditService);
    }
}
