package se.comerit.seb.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalServiceTest {

    @Test
    void finalApproval_shouldCompletePaymentDeductBalanceAndRecordAudit() {
        // ARRANGE
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalService approvalService =
                new ApprovalService(paymentRepository, accountRepository, auditService);

        Long tenantId = 1L;
        Long actorId = 2L;
        Long paymentId = 100L;
        Long approvalStepId = 200L;
        Long accountId = 10L;
        BigDecimal paymentAmount = new BigDecimal("200.00");
        BigDecimal startingBalance = new BigDecimal("1000.00");
        BigDecimal expectedFinalBalance = new BigDecimal("800.00");
        String toIban = "SE8550000000054910000003";

        Payment payment = new Payment(
                tenantId, accountId, toIban, paymentAmount, "Testfaktura", actorId);
        ApprovalStep approvalStep = new ApprovalStep(actorId, 1);
        payment.addApprovalStep(approvalStep);

        ReflectionTestUtils.setField(payment, "id", paymentId);
        ReflectionTestUtils.setField(approvalStep, "id", approvalStepId);

        Account account = mock(Account.class);
        when(account.getBalance()).thenReturn(startingBalance);
        when(paymentRepository.findByApprovalStepIdForUpdate(approvalStepId))
                .thenReturn(Optional.of(payment));
        when(accountRepository.findById(10)).thenReturn(Optional.of(account));

        // ACT
        approvalService.approve(approvalStepId, actorId);

        // ASSERT
        assertEquals(ApprovalStepStatus.APPROVED, approvalStep.getStatus());
        assertNotNull(approvalStep.getDecidedAt());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertNotNull(payment.getExecutedAt());

        verify(account).setBalance(expectedFinalBalance);
        verify(auditService, times(1)).record(
                tenantId,
                actorId,
                "APPROVE_PAYMENT",
                "PAYMENT",
                paymentId,
                "Betalning godkänd: 200.00 SEK till SE8550000000054910000003"
        );
    }

    @Test
    void approve_shouldThrowAccessDenied_whenActorIsNotTheAssignedAttestant() {
        // ARRANGE
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalService approvalService =
                new ApprovalService(paymentRepository, accountRepository, auditService);

        Long tenantId = 1L;
        Long rightfulAttestantId = 2L;   // steget tillhör denna attestant (A)
        Long intruderActorId = 3L;       // en annan attestant (B) försöker godkänna
        Long paymentId = 100L;
        Long approvalStepId = 200L;
        Long accountId = 10L;
        BigDecimal paymentAmount = new BigDecimal("200.00");
        String toIban = "SE8550000000054910000003";

        Payment payment = new Payment(
                tenantId, accountId, toIban, paymentAmount, "Testfaktura", rightfulAttestantId);
        ApprovalStep approvalStep = new ApprovalStep(rightfulAttestantId, 1);
        payment.addApprovalStep(approvalStep);

        ReflectionTestUtils.setField(payment, "id", paymentId);
        ReflectionTestUtils.setField(approvalStep, "id", approvalStepId);

        when(paymentRepository.findByApprovalStepIdForUpdate(approvalStepId))
                .thenReturn(Optional.of(payment));

        // ACT + ASSERT
        assertThrows(
                se.comerit.seb.exception.ApprovalStepAccessDeniedException.class,
                () -> approvalService.approve(approvalStepId, intruderActorId)
        );

        // Steget ska fortfarande vara PENDING — B lyckades inte ändra något
        assertEquals(ApprovalStepStatus.PENDING, approvalStep.getStatus());

        // Ingen audit-post, inget kontosaldo ska ha rörts
        verify(auditService, times(0)).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reject_shouldThrowAccessDenied_whenActorIsNotTheAssignedAttestant() {
        // ARRANGE
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalService approvalService =
                new ApprovalService(paymentRepository, accountRepository, auditService);

        Long tenantId = 1L;
        Long rightfulAttestantId = 2L;   // steget tillhör denna attestant (A)
        Long intruderActorId = 3L;       // en annan attestant (B) försöker avvisa
        Long paymentId = 100L;
        Long approvalStepId = 200L;
        Long accountId = 10L;
        BigDecimal paymentAmount = new BigDecimal("200.00");
        String toIban = "SE8550000000054910000003";

        Payment payment = new Payment(
                tenantId, accountId, toIban, paymentAmount, "Testfaktura", rightfulAttestantId);
        ApprovalStep approvalStep = new ApprovalStep(rightfulAttestantId, 1);
        payment.addApprovalStep(approvalStep);

        ReflectionTestUtils.setField(payment, "id", paymentId);
        ReflectionTestUtils.setField(approvalStep, "id", approvalStepId);

        when(paymentRepository.findByApprovalStepIdForUpdate(approvalStepId))
                .thenReturn(Optional.of(payment));

        // ACT + ASSERT
        assertThrows(
                se.comerit.seb.exception.ApprovalStepAccessDeniedException.class,
                () -> approvalService.reject(approvalStepId, intruderActorId, "Fel person")
        );

        // Varken steget eller betalningen ska ha ändrats — B lyckades inte avvisa något
        assertEquals(ApprovalStepStatus.PENDING, approvalStep.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        assertNull(approvalStep.getComment());

        // Ingen audit-post ska ha skrivits
        verifyNoInteractions(auditService);
    }

    @Test
    void countPendingByAttestant_shouldReturnNumberOfPendingStepsForUser() {
        // ARRANGE
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalService approvalService =
                new ApprovalService(paymentRepository, accountRepository, auditService);

        Long tenantId = 1L;
        Long attestantId = 2L;

        AuthenticatedUserContext user =
                new AuthenticatedUserContext(attestantId, tenantId, se.comerit.seb.domain.Role.ATTESTANT);

        Payment payment1 = new Payment(
                tenantId, 10L, "SE123", new BigDecimal("100.00"), "Test 1", 5L);
        Payment payment2 = new Payment(
                tenantId, 11L, "SE456", new BigDecimal("200.00"), "Test 2", 5L);

        ApprovalStep pendingStep1 = new ApprovalStep(attestantId, 1);
        ApprovalStep pendingStep2 = new ApprovalStep(attestantId, 1);
        ApprovalStep approvedStep = new ApprovalStep(attestantId, 2);
        approvedStep.setStatus(ApprovalStepStatus.APPROVED);

        payment1.addApprovalStep(pendingStep1);
        payment1.addApprovalStep(approvedStep);
        payment2.addApprovalStep(pendingStep2);

        when(paymentRepository.findPendingApprovalsForAttestant(tenantId, attestantId))
                .thenReturn(List.of(payment1, payment2));

        // ACT
        int result = approvalService.countPendingByAttestant(user);

        // ASSERT
        assertEquals(2, result);

        verify(paymentRepository, times(1))
                .findPendingApprovalsForAttestant(tenantId, attestantId);
    }

    @Test
    void approve_concurrentApproval_shouldThrowAndRollback() {
        // ARRANGE
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalService approvalService =
                new ApprovalService(paymentRepository, accountRepository, auditService);

        Long tenantId = 1L;
        Long actorId = 2L;
        Long paymentId = 100L;
        Long approvalStep1Id = 200L;
        Long approvalStep2Id = 201L;
        Long accountId = 10L;
        BigDecimal paymentAmount = new BigDecimal("200.00");
        String toIban = "SE8550000000054910000003";

        Payment payment = new Payment(
                tenantId, accountId, toIban, paymentAmount, "Testfaktura", actorId);

        ApprovalStep approvalStep1 = new ApprovalStep(actorId, 1);
        payment.addApprovalStep(approvalStep1);

        ApprovalStep approvalStep2 = new ApprovalStep(actorId, 2);
        payment.addApprovalStep(approvalStep2);

        ReflectionTestUtils.setField(payment, "id", paymentId);
        ReflectionTestUtils.setField(approvalStep1, "id", approvalStep1Id);
        ReflectionTestUtils.setField(approvalStep2, "id", approvalStep2Id);

        when(paymentRepository.findByApprovalStepIdForUpdate(approvalStep2Id))
                .thenReturn(Optional.of(payment));

        // ACT + ASSERT
        assertThrows(
                IllegalStateException.class,
                () -> approvalService.approve(approvalStep2Id, actorId)
        );

        // Steg 2 ska fortfarande vara PENDING — godkännandet gick aldrig igenom
        assertEquals(ApprovalStepStatus.PENDING, approvalStep2.getStatus());

        // Steg 1 ska också fortfarande vara PENDING — orört
        assertEquals(ApprovalStepStatus.PENDING, approvalStep1.getStatus());

        // Ingen audit-post ska ha skapats
        verify(auditService, times(0)).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
