package se.comerit.seb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.SecurityConfig;
import se.comerit.seb.domain.Role;
import se.comerit.seb.exception.ApprovalStepAccessDeniedException;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.ApprovalService;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Detta är #116:s ursprungliga testkrav, skrivet mot hela den riktiga kedjan
// (filter -> SecurityConfig -> controller -> service -> GlobalExceptionHandler),
// inte mot mockade delar av den. Bara ApprovalApiController testas här - samma
// JWT-mekanik är redan bevisad, att upprepa den för AuditController/NewPaymentController
// hade bara varit repetition utan nytt värde.
@WebMvcTest(ApprovalApiController.class)
@Import({SecurityConfig.class, JwtService.class, JwtUserContext.class})
class ApprovalApiControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private ApprovalService approvalService;

    @Test
    void approve_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/approvals/1/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approve_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(post("/api/approvals/1/approve")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approve_asWrongAttestant_returns403() throws Exception {
        // Attestant 99 försöker godkänna steg 1, som tillhör någon annan.
        // Själva ägarkontrollen ligger i ApprovalService (redan testad i
        // ApprovalServiceTest) - här verifieras att HELA kedjan landar på 403.
        String attestantBToken = jwtService.generateToken(
                new AuthenticatedUserContext(99L, 1L, Role.ATTESTANT));

        doThrow(new ApprovalStepAccessDeniedException("Approval step 1 is not assigned to actor 99"))
                .when(approvalService).approve(1L, 99L);

        mockMvc.perform(post("/api/approvals/1/approve")
                        .header("Authorization", "Bearer " + attestantBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_ownStep_succeeds() throws Exception {
        // Happy path: attestant 1 godkänner sitt eget steg 1.
        String attestantAToken = jwtService.generateToken(
                new AuthenticatedUserContext(1L, 1L, Role.ATTESTANT));

        doNothing().when(approvalService).approve(1L, 1L);

        mockMvc.perform(post("/api/approvals/1/approve")
                        .header("Authorization", "Bearer " + attestantAToken))
                .andExpect(status().isNoContent());
    }
}
