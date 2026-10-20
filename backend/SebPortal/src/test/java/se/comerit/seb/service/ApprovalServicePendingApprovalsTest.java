package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.dto.PendingApprovalResponse;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// UX-feedback: attestkorgen visar vilket konto som belastas och vem som skapade betalningen,
// så att attestanten vet vem som kan svara på frågor om den.
class ApprovalServicePendingApprovalsTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER_TENANT = 2L;
    private static final Long INITIATOR_ID = 10L;
    private static final Long ATTESTANT_ID = 20L;
    private static final Long OTHER_ATTESTANT_ID = 21L;

    private final AuthenticatedUserContext attestant =
            new AuthenticatedUserContext(ATTESTANT_ID, TENANT, Role.ATTESTANT);

    private PaymentRepository paymentRepository;
    private AccountRepository accountRepository;
    private UserRepository userRepository;
    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        accountRepository = mock(AccountRepository.class);
        userRepository = mock(UserRepository.class);
        approvalService = new ApprovalService(paymentRepository, accountRepository, mock(AuditService.class),
                userRepository);
    }

    @Test
    void showsWhichAccountIsDrawnFromAndWhoCreatedThePayment() {
        Account driftkonto = account(1, "Driftkonto");
        User lisa = user(INITIATOR_ID, TENANT, "Lisa Persson");
        when(paymentRepository.findPendingApprovalsForAttestant(TENANT, ATTESTANT_ID))
                .thenReturn(List.of(payment(100L, 1L, INITIATOR_ID, ATTESTANT_ID)));
        when(accountRepository.findByTenantIdOrderByIdAsc(TENANT)).thenReturn(List.of(driftkonto));
        when(userRepository.findAllById(List.of(INITIATOR_ID))).thenReturn(List.of(lisa));

        PendingApprovalResponse approval = approvalService.getPendingApprovals(attestant).get(0);

        assertEquals("Driftkonto", approval.fromAccountName());
        assertEquals("Lisa Persson", approval.initiatorName());
    }

    @Test
    void anotherCompanysAccountOrUser_isNeverNamed() {
        // Kontofrågan hämtar bara det egna företagets konton; betalningens konto 1 finns inte bland dem
        Account otherAccount = account(2, "Lönekonto");
        User outsider = user(INITIATOR_ID, OTHER_TENANT, "Annan Firma");
        when(paymentRepository.findPendingApprovalsForAttestant(TENANT, ATTESTANT_ID))
                .thenReturn(List.of(payment(100L, 1L, INITIATOR_ID, ATTESTANT_ID)));
        when(accountRepository.findByTenantIdOrderByIdAsc(TENANT)).thenReturn(List.of(otherAccount));
        when(userRepository.findAllById(List.of(INITIATOR_ID))).thenReturn(List.of(outsider));

        PendingApprovalResponse approval = approvalService.getPendingApprovals(attestant).get(0);

        assertNull(approval.fromAccountName());
        assertNull(approval.initiatorName());
    }

    @Test
    void listsOnlyTheLoggedInAttestantsOwnPendingSteps() {
        Payment shared = payment(100L, 1L, INITIATOR_ID, ATTESTANT_ID, OTHER_ATTESTANT_ID);
        when(paymentRepository.findPendingApprovalsForAttestant(TENANT, ATTESTANT_ID)).thenReturn(List.of(shared));

        List<PendingApprovalResponse> approvals = approvalService.getPendingApprovals(attestant);

        assertEquals(1, approvals.size());
        assertEquals(shared.getApprovalSteps().get(0).getId(), approvals.get(0).id());
    }

    @Test
    void namesAreLookedUpOnceForTheWholeList() {
        when(paymentRepository.findPendingApprovalsForAttestant(TENANT, ATTESTANT_ID))
                .thenReturn(List.of(
                        payment(100L, 1L, INITIATOR_ID, ATTESTANT_ID),
                        payment(101L, 1L, INITIATOR_ID, ATTESTANT_ID)));

        approvalService.getPendingApprovals(attestant);

        verify(accountRepository, times(1)).findByTenantIdOrderByIdAsc(TENANT);
        verify(userRepository, times(1)).findAllById(anyList());
    }

    private static Payment payment(Long id, Long fromAccountId, Long createdBy, Long... attestantIds) {
        Payment payment = new Payment(TENANT, fromAccountId, "SE4550000000058398257466",
                new BigDecimal("7500.00"), "Demo", createdBy);
        ReflectionTestUtils.setField(payment, "id", id);
        long stepId = id * 10;
        int stepNumber = 1;
        for (Long attestantId : attestantIds) {
            ApprovalStep step = new ApprovalStep(attestantId, stepNumber++);
            ReflectionTestUtils.setField(step, "id", stepId++);
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
        User user = new User(tenantId, name, "user" + id + "@example.com", "hash", Role.INITIATOR);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
