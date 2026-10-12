package se.comerit.seb.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.Role;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.RoleAccessDeniedHandler;
import se.comerit.seb.service.ApprovalService;
import se.comerit.seb.service.AuditService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #197: /api/approvals/count (siffran i sidomenyns badge) genom hela kedjan: JWT-filter ->
// controller -> RIKTIGA ApprovalService. Repositoryt är mockat, men returnerar som den riktiga
// frågan hela betalningar, alltså även andra attestanters steg. Det är filtreringen i servicen
// som ska se till att bara den inloggade attestantens egna väntande steg räknas.
@WebMvcTest(ApprovalApiController.class)
@Import({JwtSecurityConfig.class, RoleAccessDeniedHandler.class, JwtService.class, JwtUserContext.class, ApprovalService.class})
class ApprovalApiControllerCountTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ATTESTANT_A_ID = 2L;
    private static final Long ATTESTANT_B_ID = 3L;

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

    @BeforeEach
    void setUp() {
        // Betalning 1 kräver både A och B, betalning 2 bara A.
        Payment sharedPayment = paymentWithStepsFor(ATTESTANT_A_ID, ATTESTANT_B_ID);
        Payment onlyAPayment = paymentWithStepsFor(ATTESTANT_A_ID);

        when(paymentRepository.findPendingApprovalsForAttestant(TENANT_ID, ATTESTANT_A_ID))
                .thenReturn(List.of(sharedPayment, onlyAPayment));
        when(paymentRepository.findPendingApprovalsForAttestant(TENANT_ID, ATTESTANT_B_ID))
                .thenReturn(List.of(sharedPayment));
    }

    @Test
    void count_returnsOnlyTheLoggedInAttestantsPendingSteps() throws Exception {
        mockMvc.perform(get("/api/approvals/count")
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_A_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        verify(paymentRepository).findPendingApprovalsForAttestant(TENANT_ID, ATTESTANT_A_ID);
    }

    @Test
    void count_doesNotIncludeOtherAttestantsStepsOnASharedPayment() throws Exception {
        mockMvc.perform(get("/api/approvals/count")
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_B_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void count_isZeroWhenNothingIsWaiting() throws Exception {
        Long attestantWithNothingToDo = 4L;
        when(paymentRepository.findPendingApprovalsForAttestant(TENANT_ID, attestantWithNothingToDo))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/approvals/count")
                        .header("Authorization", "Bearer " + tokenFor(attestantWithNothingToDo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    private Payment paymentWithStepsFor(Long... attestantIds) {
        Payment payment = new Payment(TENANT_ID, 10L, "SE8550000000054910000003",
                new BigDecimal("200.00"), "Testfaktura", 1L);
        for (int i = 0; i < attestantIds.length; i++) {
            payment.addApprovalStep(new ApprovalStep(attestantIds[i], i + 1));
        }
        return payment;
    }

    private String tokenFor(Long userId) {
        return jwtService.generateToken(new AuthenticatedUserContext(userId, TENANT_ID, Role.ATTESTANT));
    }
}
