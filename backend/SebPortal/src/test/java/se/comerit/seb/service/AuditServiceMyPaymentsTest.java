package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.MyPaymentStatusResponse;
import se.comerit.seb.dto.MyPaymentStatusResponse.ApprovalStepSummary;
import se.comerit.seb.infrastructure.audit.AuditSignerService;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.AuditRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// UX-feedback: Mina betalningar visar vem som ska attestera, så att initiatören vet vem som ska
// kontaktas om en betalning fastnar, och vilket konto betalningen dras från.
class AuditServiceMyPaymentsTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER_TENANT = 2L;
    private static final Long INITIATOR_ID = 10L;
    private static final Long ATTESTANT_ID = 20L;
    private static final Long CEO_ID = 30L;

    private final AuthenticatedUserContext initiator =
            new AuthenticatedUserContext(INITIATOR_ID, TENANT, Role.INITIATOR);

    private PaymentRepository paymentRepository;
    private UserRepository userRepository;
    private AccountRepository accountRepository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        userRepository = mock(UserRepository.class);
        accountRepository = mock(AccountRepository.class);
        auditService = new AuditService(mock(AuditRepository.class), userRepository, paymentRepository,
                accountRepository, mock(AuditSignerService.class));
    }

    @Test
    void showsWhichAccountThePaymentIsDrawnFrom() {
        Account driftkonto = account(1, "Driftkonto");
        when(accountRepository.findByTenantIdOrderByIdAsc(TENANT)).thenReturn(List.of(driftkonto));
        when(paymentRepository.findByTenantIdAndCreatedByWithApprovalSteps(TENANT, INITIATOR_ID))
                .thenReturn(List.of(payment(100L)));

        MyPaymentStatusResponse response = auditService.getMyPaymentStatuses(initiator).get(0);

        assertEquals("Driftkonto", response.fromAccountName());
    }

    @Test
    void anAccountOutsideTheCompany_isNeverNamed() {
        // Frågan hämtar bara det egna företagets konton; betalningens konto finns inte bland dem
        Account lonekonto = account(2, "Lönekonto");
        when(accountRepository.findByTenantIdOrderByIdAsc(TENANT)).thenReturn(List.of(lonekonto));
        when(paymentRepository.findByTenantIdAndCreatedByWithApprovalSteps(TENANT, INITIATOR_ID))
                .thenReturn(List.of(payment(100L)));

        MyPaymentStatusResponse response = auditService.getMyPaymentStatuses(initiator).get(0);

        assertNull(response.fromAccountName());
    }

    @Test
    void pendingPayment_showsWhichAttestantItIsWaitingFor() {
        Payment payment = payment(100L, new ApprovalStep(ATTESTANT_ID, 1));
        when(paymentRepository.findByTenantIdAndCreatedByWithApprovalSteps(TENANT, INITIATOR_ID))
                .thenReturn(List.of(payment));
        when(userRepository.findAllById(List.of(ATTESTANT_ID)))
                .thenReturn(List.of(user(ATTESTANT_ID, TENANT, "Johan Berg")));

        MyPaymentStatusResponse response = auditService.getMyPaymentStatuses(initiator).get(0);

        assertEquals(List.of(new ApprovalStepSummary(1, "Johan Berg", "PENDING", null)), response.approvalSteps());
    }

    @Test
    void decidedSteps_carryTheDecisionAndWhenItWasMade_inStepOrder() {
        LocalDateTime decidedAt = LocalDateTime.of(2026, 10, 15, 9, 30);
        ApprovalStep first = new ApprovalStep(ATTESTANT_ID, 1);
        first.setStatus(ApprovalStepStatus.APPROVED);
        first.setDecidedAt(decidedAt);
        Payment payment = payment(100L, first, new ApprovalStep(CEO_ID, 2));
        when(paymentRepository.findByTenantIdAndCreatedByWithApprovalSteps(TENANT, INITIATOR_ID))
                .thenReturn(List.of(payment));
        when(userRepository.findAllById(List.of(ATTESTANT_ID, CEO_ID)))
                .thenReturn(List.of(user(ATTESTANT_ID, TENANT, "Johan Berg"), user(CEO_ID, TENANT, "Vera VD")));

        List<ApprovalStepSummary> steps = auditService.getMyPaymentStatuses(initiator).get(0).approvalSteps();

        assertEquals(List.of(
                new ApprovalStepSummary(1, "Johan Berg", "APPROVED", decidedAt),
                new ApprovalStepSummary(2, "Vera VD", "PENDING", null)
        ), steps);
    }

    @Test
    void attestantNames_areLookedUpOnceForTheWholeList() {
        when(paymentRepository.findByTenantIdAndCreatedByWithApprovalSteps(TENANT, INITIATOR_ID))
                .thenReturn(List.of(
                        payment(100L, new ApprovalStep(ATTESTANT_ID, 1)),
                        payment(101L, new ApprovalStep(ATTESTANT_ID, 1))));
        when(userRepository.findAllById(List.of(ATTESTANT_ID)))
                .thenReturn(List.of(user(ATTESTANT_ID, TENANT, "Johan Berg")));

        auditService.getMyPaymentStatuses(initiator);

        verify(userRepository, times(1)).findAllById(anyList());
    }

    @Test
    void aUserFromAnotherCompany_isNeverNamed() {
        when(paymentRepository.findByTenantIdAndCreatedByWithApprovalSteps(TENANT, INITIATOR_ID))
                .thenReturn(List.of(payment(100L, new ApprovalStep(ATTESTANT_ID, 1))));
        when(userRepository.findAllById(List.of(ATTESTANT_ID)))
                .thenReturn(List.of(user(ATTESTANT_ID, OTHER_TENANT, "Annan Firma")));

        ApprovalStepSummary step = auditService.getMyPaymentStatuses(initiator).get(0).approvalSteps().get(0);

        assertEquals("Okänd attestant", step.attestantName());
    }

    @Test
    void paymentWithoutApprovalSteps_hasAnEmptyList() {
        when(paymentRepository.findByTenantIdAndCreatedByWithApprovalSteps(TENANT, INITIATOR_ID))
                .thenReturn(List.of(payment(100L)));
        when(userRepository.findAllById(List.of())).thenReturn(List.of());

        MyPaymentStatusResponse response = auditService.getMyPaymentStatuses(initiator).get(0);

        assertTrue(response.approvalSteps().isEmpty());
    }

    private static Payment payment(Long id, ApprovalStep... steps) {
        Payment payment = new Payment(TENANT, 1L, "SE4550000000058398257466",
                new BigDecimal("7500.00"), "Demo", INITIATOR_ID);
        ReflectionTestUtils.setField(payment, "id", id);
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

    private static User user(Long id, Long tenantId, String name) {
        User user = new User(tenantId, name, name.toLowerCase().replace(' ', '.') + "@example.com",
                "hash", Role.ATTESTANT);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
