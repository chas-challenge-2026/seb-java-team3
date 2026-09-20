package se.comerit.seb.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.domain.Role;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.RoleAccessDeniedHandler;
import se.comerit.seb.security.SecurityConfig;
import se.comerit.seb.security.SessionAuthenticationFilter;
import se.comerit.seb.service.ApprovalService;
import se.comerit.seb.service.AuditService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #116:s ägarkontroll genom hela kedjan: JWT-filter -> säkerhetskedjor -> controller ->
// RIKTIGA ApprovalService -> GlobalExceptionHandler. Bara persistenslagret (repositories,
// audit) är mockat, så det är den verkliga ägarkollen i servicen som ger 403 här - till
// skillnad från ApprovalApiControllerSecurityTest, där servicen själv är mockad.
@WebMvcTest(ApprovalApiController.class)
@Import({JwtSecurityConfig.class, SecurityConfig.class, SessionAuthenticationFilter.class,
        RoleAccessDeniedHandler.class, JwtService.class, JwtUserContext.class, ApprovalService.class})
class ApprovalApiControllerOwnershipTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ATTESTANT_A_ID = 2L;   // steget tillhör A
    private static final Long ATTESTANT_B_ID = 3L;   // B försöker agera på A:s steg
    private static final Long PAYMENT_ID = 100L;
    private static final Long STEP_ID = 200L;
    private static final Long ACCOUNT_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private AuditService auditService;

    private Payment payment;
    private ApprovalStep step;

    @BeforeEach
    void setUp() {
        payment = new Payment(TENANT_ID, ACCOUNT_ID, "SE8550000000054910000003",
                new BigDecimal("200.00"), "Testfaktura", 1L);
        step = new ApprovalStep(ATTESTANT_A_ID, 1);
        payment.addApprovalStep(step);

        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        ReflectionTestUtils.setField(step, "id", STEP_ID);

        when(paymentRepository.findByApprovalStepIdForUpdate(STEP_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    void approve_asAttestantWhoDoesNotOwnTheStep_returns403AndChangesNothing() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_B_ID)))
                .andExpect(status().isForbidden());

        assertEquals(ApprovalStepStatus.PENDING, step.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        verifyNoInteractions(accountRepository, auditService);
    }

    @Test
    void reject_asAttestantWhoDoesNotOwnTheStep_returns403AndChangesNothing() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/reject", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_B_ID))
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"Fel person\"}"))
                .andExpect(status().isForbidden());

        assertEquals(ApprovalStepStatus.PENDING, step.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        assertNull(step.getComment());
        verifyNoInteractions(accountRepository, auditService);
    }

    @Test
    void approve_asAttestantWhoOwnsTheStep_returns204AndApprovesTheStep() throws Exception {
        Account account = mock(Account.class);
        when(account.getBalance()).thenReturn(new BigDecimal("1000.00"));
        when(accountRepository.findById(ACCOUNT_ID.intValue())).thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_A_ID)))
                .andExpect(status().isNoContent());

        assertEquals(ApprovalStepStatus.APPROVED, step.getStatus());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
    }

    private String tokenFor(Long userId) {
        return jwtService.generateToken(new AuthenticatedUserContext(userId, TENANT_ID, Role.ATTESTANT));
    }
}
