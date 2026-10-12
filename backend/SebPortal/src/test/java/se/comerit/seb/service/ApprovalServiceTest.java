package se.comerit.seb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        // Kontot hämtas med betalningens företag (tenant) och låses; se findByIdAndTenantIdForUpdate
        when(accountRepository.findByIdAndTenantIdForUpdate(10, tenantId)).thenReturn(Optional.of(account));

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
    void approve_shouldNotCompletePaymentWhileAnotherStepIsStillPending() {
        // ARRANGE - utförandegrinden: en betalning med två steg där bara steg 1 godkänns
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalService approvalService =
                new ApprovalService(paymentRepository, accountRepository, auditService);

        Long actorId = 2L;
        Long approvalStep1Id = 200L;
        Long approvalStep2Id = 201L;

        Payment payment = new Payment(
                1L, 10L, "SE8550000000054910000003", new BigDecimal("200.00"), "Testfaktura", 1L);

        ApprovalStep approvalStep1 = new ApprovalStep(actorId, 1);
        payment.addApprovalStep(approvalStep1);
        ApprovalStep approvalStep2 = new ApprovalStep(3L, 2);
        payment.addApprovalStep(approvalStep2);

        ReflectionTestUtils.setField(payment, "id", 100L);
        ReflectionTestUtils.setField(approvalStep1, "id", approvalStep1Id);
        ReflectionTestUtils.setField(approvalStep2, "id", approvalStep2Id);

        when(paymentRepository.findByApprovalStepIdForUpdate(approvalStep1Id))
                .thenReturn(Optional.of(payment));

        // ACT
        approvalService.approve(approvalStep1Id, actorId);

        // ASSERT: steg 1 är godkänt, men steg 2 är ogodkänt -> betalningen får INTE genomföras
        assertEquals(ApprovalStepStatus.APPROVED, approvalStep1.getStatus());
        assertEquals(ApprovalStepStatus.PENDING, approvalStep2.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        assertNull(payment.getExecutedAt());

        // Saldot ska vara orört och ingen "godkänd betalning"-audit får ha skrivits
        verifyNoInteractions(accountRepository, auditService);
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

    @Test
    void approve_shouldRefuseToDebitAccountOfAnotherTenant() {
        // ARRANGE - R-04: betalningen tillhör företag 1 och pekar på konto 10, men konto 10 ägs av
        // ett annat företag. Frågan är tenant-scopad, så databasen svarar "inget konto" (tomt).
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalService approvalService =
                new ApprovalService(paymentRepository, accountRepository, auditService);

        Long tenantId = 1L;
        Long actorId = 2L;
        Payment payment = paymentWithPendingStep(actorId, tenantId, 10L);

        when(paymentRepository.findByApprovalStepIdForUpdate(200L)).thenReturn(Optional.of(payment));
        when(accountRepository.findByIdAndTenantIdForUpdate(10, tenantId)).thenReturn(Optional.empty());

        // ACT + ASSERT
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> approvalService.approve(200L, actorId)
        );
        assertEquals("Från-kontot finns inte eller tillhör inte ditt företag", ex.getMessage());

        // Kontot slås upp med betalningens företag och aldrig utan tenant (inget findById-fallback)
        verify(accountRepository).findByIdAndTenantIdForUpdate(10, tenantId);
        verify(accountRepository, never()).findById(anyInt());

        // Steget hinner markeras i minnet innan undantaget kastas; att inget sparas garanteras av
        // @Transactional (rollback vid RuntimeException). Därför kontrolleras här det som inte får
        // hända alls: betalningen slutförs inte och ingen audit-post skrivs.
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        assertNull(payment.getExecutedAt());
        verifyNoInteractions(auditService);
    }

    // 0 och -1 är aldrig giltiga konto-id. 2 147 483 648 ryms inte i INT, och 4 294 967 297 skulle
    // "slå runt" till konto 1 vid omvandling till int; null är en betalning som saknar från-konto
    // (äldre rader eller manipulerad data). Alla ska nekas innan någon databasfråga görs.
    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L, 2_147_483_648L, 4_294_967_297L})
    void approve_shouldRefuseInvalidFromAccountIdWithoutLookup(Long invalidAccountId) {
        // ARRANGE
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalService approvalService =
                new ApprovalService(paymentRepository, accountRepository, auditService);

        Long actorId = 2L;
        Payment payment = paymentWithPendingStep(actorId, 1L, invalidAccountId);
        when(paymentRepository.findByApprovalStepIdForUpdate(200L)).thenReturn(Optional.of(payment));

        // ACT + ASSERT
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> approvalService.approve(200L, actorId)
        );
        assertEquals("Från-kontot finns inte eller tillhör inte ditt företag", ex.getMessage());

        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        verifyNoInteractions(accountRepository, auditService);
    }

    // Bygger en betalning med ett väntande steg åt attestanten (steg-id 200, betalning-id 100),
    // redo att godkännas.
    private static Payment paymentWithPendingStep(Long attestantId, Long tenantId, Long fromAccountId) {
        Payment payment = new Payment(
                tenantId, fromAccountId, "SE8550000000054910000003",
                new BigDecimal("200.00"), "Testfaktura", attestantId);
        ApprovalStep step = new ApprovalStep(attestantId, 1);
        payment.addApprovalStep(step);

        ReflectionTestUtils.setField(payment, "id", 100L);
        ReflectionTestUtils.setField(step, "id", 200L);
        return payment;
    }
}
