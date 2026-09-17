package se.comerit.seb.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.controller.ApprovalApiController;
import se.comerit.seb.controller.AuditController;
import se.comerit.seb.controller.NewPaymentController;
import se.comerit.seb.domain.Role;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.service.ApprovalService;
import se.comerit.seb.service.AuditService;
import se.comerit.seb.service.PaymentService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the endpoint-level role matrix declared via @PreAuthorize. Requests are driven
 * end-to-end through the real SecurityFilterChain/JwtAuthenticationFilter (Bearer token ->
 * ROLE_* GrantedAuthority) rather than by faking the security context, so this also verifies
 * that piece of the chain, not just the annotations. All endpoints under test live under
 * /api/**, which is stateless/JWT-only (see se.comerit.seb.config.JwtSecurityConfig) - there is
 * no session to attach a role to anymore, hence generating a real token per case.
 */
@WebMvcTest(controllers = {ApprovalApiController.class, AuditController.class, NewPaymentController.class})
@Import({JwtSecurityConfig.class, SecurityConfig.class, SessionAuthenticationFilter.class,
        RoleAccessDeniedHandler.class, SessionUserContext.class, JwtService.class, JwtUserContext.class})
class RoleAuthorizationTest {

    private static final String PAYMENT_REQUEST_JSON = """
            {"fromAccountId":1,"toIban":"SE1234567890123456789012","amount":100.00,"reference":"test"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private PaymentService paymentService;

    private String tokenFor(Long userId, Long tenantId, Role role) {
        return jwtService.generateToken(new AuthenticatedUserContext(userId, tenantId, role));
    }

    @Test
    void initiatorCannotListPendingApprovals() throws Exception {
        mockMvc.perform(get("/api/approvals")
                        .header("Authorization", "Bearer " + tokenFor(1L, 1L, Role.INITIATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void attestantCanListPendingApprovals() throws Exception {
        when(paymentRepository.findPendingApprovalsForAttestant(anyLong(), anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/approvals")
                        .header("Authorization", "Bearer " + tokenFor(2L, 1L, Role.ATTESTANT)))
                .andExpect(status().isOk());
    }

    @Test
    void jwtAuthenticationWinsWhenLegacySessionCookieIsPresent() throws Exception {
        MockHttpSession legacySession = new MockHttpSession();
        legacySession.setAttribute("userId", 1L);
        legacySession.setAttribute("role", Role.INITIATOR);

        when(paymentRepository.findPendingApprovalsForAttestant(anyLong(), anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/approvals")
                        .session(legacySession)
                        .header("Authorization", "Bearer " + tokenFor(2L, 1L, Role.ATTESTANT)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanListPendingApprovals() throws Exception {
        when(paymentRepository.findPendingApprovalsForAttestant(anyLong(), anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/approvals")
                        .header("Authorization", "Bearer " + tokenFor(3L, 1L, Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void initiatorCannotApprovePayments() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/approve", 10L)
                        .header("Authorization", "Bearer " + tokenFor(1L, 1L, Role.INITIATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void attestantCanApprovePayments() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/approve", 10L)
                        .header("Authorization", "Bearer " + tokenFor(2L, 1L, Role.ATTESTANT)))
                .andExpect(status().isNoContent());
    }

    @Test
    void initiatorCannotRejectPayments() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/reject", 10L)
                        .header("Authorization", "Bearer " + tokenFor(1L, 1L, Role.INITIATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanRejectPayments() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/reject", 10L)
                        .header("Authorization", "Bearer " + tokenFor(3L, 1L, Role.ADMIN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void initiatorCannotViewAuditLog() throws Exception {
        mockMvc.perform(get("/api/audit")
                        .header("Authorization", "Bearer " + tokenFor(1L, 1L, Role.INITIATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void attestantCanViewAuditLog() throws Exception {
        when(auditService.getAuditEntries(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/audit")
                        .header("Authorization", "Bearer " + tokenFor(2L, 1L, Role.ATTESTANT)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanViewAuditLog() throws Exception {
        when(auditService.getAuditEntries(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/audit")
                        .header("Authorization", "Bearer " + tokenFor(3L, 1L, Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void attestantCanViewPaymentAuditTimeline() throws Exception {
        when(auditService.getPaymentAuditTimeline(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/payments/{paymentId}/audit", 100L)
                        .header("Authorization", "Bearer " + tokenFor(2L, 1L, Role.ATTESTANT)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanViewPaymentAuditTimeline() throws Exception {
        when(auditService.getPaymentAuditTimeline(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/payments/{paymentId}/audit", 100L)
                        .header("Authorization", "Bearer " + tokenFor(3L, 1L, Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/approvals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void attestantCannotCreatePayment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + tokenFor(2L, 1L, Role.ATTESTANT))
                        .contentType(APPLICATION_JSON)
                        .content(PAYMENT_REQUEST_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void initiatorCanCreatePayment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + tokenFor(1L, 1L, Role.INITIATOR))
                        .contentType(APPLICATION_JSON)
                        .content(PAYMENT_REQUEST_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void adminCanCreatePayment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + tokenFor(3L, 1L, Role.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(PAYMENT_REQUEST_JSON))
                .andExpect(status().isCreated());
    }
}
