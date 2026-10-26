package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.AuditEntryResponse;
import se.comerit.seb.infrastructure.audit.AuditSignerService;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.AuditRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// UX-feedback för auditloggen: vilket konto betalningen dras från, och om den godkändes automatiskt
// (under tröskeln, inga atteststeg).
class AuditServiceAuditEntriesTest {

    private static final Long TENANT = 1L;
    private static final Long INITIATOR_ID = 10L;
    private static final Long ATTESTANT_ID = 20L;

    private final AuthenticatedUserContext admin = new AuthenticatedUserContext(1L, TENANT, Role.ADMIN);

    private AuditRepository auditRepository;
    private PaymentRepository paymentRepository;
    private AccountRepository accountRepository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditRepository = mock(AuditRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        accountRepository = mock(AccountRepository.class);
        auditService = new AuditService(auditRepository, mock(UserRepository.class), paymentRepository,
                accountRepository, mock(AuditSignerService.class));
    }

    @Test
    void completedPaymentWithoutApprovalSteps_isAutoApproved() {
        givenPaymentEvent(payment(100L, PaymentStatus.COMPLETED));

        assertTrue(singleEntry().autoApproved());
    }

    @Test
    void attestedPayment_isNotAutoApproved() {
        ApprovalStep approved = new ApprovalStep(ATTESTANT_ID, 1);
        approved.setStatus(ApprovalStepStatus.APPROVED);
        givenPaymentEvent(payment(100L, PaymentStatus.COMPLETED, approved));

        assertFalse(singleEntry().autoApproved());
    }

    @Test
    void paymentWaitingForApproval_isNotAutoApproved() {
        givenPaymentEvent(payment(100L, PaymentStatus.PENDING_APPROVAL, new ApprovalStep(ATTESTANT_ID, 1)));

        assertFalse(singleEntry().autoApproved());
    }

    @Test
    void showsWhichAccountThePaymentIsDrawnFrom() {
        Account driftkonto = account(1, "Driftkonto");
        when(accountRepository.findByTenantIdOrderByIdAsc(TENANT)).thenReturn(List.of(driftkonto));
        givenPaymentEvent(payment(100L, PaymentStatus.COMPLETED));

        assertEquals("Driftkonto", singleEntry().fromAccountName());
    }

    @Test
    void anAccountOutsideTheCompany_isNeverNamed() {
        // Kontofrågan hämtar bara företagets egna konton; betalningens konto 1 finns inte bland dem
        Account otherAccount = account(2, "Lönekonto");
        when(accountRepository.findByTenantIdOrderByIdAsc(TENANT)).thenReturn(List.of(otherAccount));
        givenPaymentEvent(payment(100L, PaymentStatus.COMPLETED));

        assertNull(singleEntry().fromAccountName());
    }

    @Test
    void paymentsAreLookedUpWithinTheCompanyOnly() {
        givenPaymentEvent(payment(100L, PaymentStatus.COMPLETED));

        singleEntry();

        verify(paymentRepository).findByTenantIdAndIdInWithApprovalSteps(TENANT, List.of(100L));
    }

    @Test
    void noEvents_noPaymentQuery() {
        when(auditRepository.findTop200ByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());

        assertTrue(auditService.getAuditEntries(admin).isEmpty());
        verify(paymentRepository, never()).findByTenantIdAndIdInWithApprovalSteps(any(), any());
    }

    private void givenPaymentEvent(Payment payment) {
        AuditEntry created = new AuditEntry(TENANT, INITIATOR_ID, "CREATE_PAYMENT", "PAYMENT",
                payment.getId(), "Betalning skapad");
        when(auditRepository.findTop200ByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of(created));
        when(paymentRepository.findByTenantIdAndIdInWithApprovalSteps(TENANT, List.of(payment.getId())))
                .thenReturn(List.of(payment));
    }

    private AuditEntryResponse singleEntry() {
        List<AuditEntryResponse> entries = auditService.getAuditEntries(admin);
        assertEquals(1, entries.size());
        return entries.get(0);
    }

    private static Payment payment(Long id, PaymentStatus status, ApprovalStep... steps) {
        Payment payment = new Payment(TENANT, 1L, "SE4550000000058398257466",
                new BigDecimal("4500.00"), "Demo", INITIATOR_ID);
        ReflectionTestUtils.setField(payment, "id", id);
        payment.setStatus(status);
        for (ApprovalStep step : steps) {
            payment.addApprovalStep(step);
        }
        return payment;
    }

    private static Account account(Integer id, String name) {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(id);
        when(account.getAccountName()).thenReturn(name);
        return account;
    }
}
