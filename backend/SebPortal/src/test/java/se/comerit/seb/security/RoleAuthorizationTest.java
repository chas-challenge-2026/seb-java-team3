package se.comerit.seb.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.controller.ApprovalApiController;
import se.comerit.seb.controller.AuditController;
import se.comerit.seb.domain.Role;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.service.ApprovalService;
import se.comerit.seb.service.AuditService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the endpoint-level role matrix declared via @PreAuthorize. Requests are driven
 * end-to-end through the real SecurityFilterChain/SessionAuthenticationFilter (session role
 * -> ROLE_* GrantedAuthority) rather than by faking the security context, so this also
 * verifies that piece of the chain, not just the annotations.
 */
@WebMvcTest(controllers = {ApprovalApiController.class, AuditController.class})
@Import({SecurityConfig.class, SessionAuthenticationFilter.class, RoleAccessDeniedHandler.class,
        SessionUserContext.class})
class RoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private AuditService auditService;

    @Test
    void initiatorCannotListPendingApprovals() throws Exception {
        mockMvc.perform(get("/api/approvals")
                        .sessionAttr("userId", 1L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.INITIATOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void attestantCanListPendingApprovals() throws Exception {
        when(paymentRepository.findPendingApprovalsForAttestant(anyLong(), anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/approvals")
                        .sessionAttr("userId", 2L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.ATTESTANT))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanListPendingApprovals() throws Exception {
        when(paymentRepository.findPendingApprovalsForAttestant(anyLong(), anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/approvals")
                        .sessionAttr("userId", 3L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void initiatorCannotApprovePayments() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/approve", 10L)
                        .sessionAttr("userId", 1L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.INITIATOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void attestantCanApprovePayments() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/approve", 10L)
                        .sessionAttr("userId", 2L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.ATTESTANT))
                .andExpect(status().isNoContent());
    }

    @Test
    void initiatorCannotRejectPayments() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/reject", 10L)
                        .sessionAttr("userId", 1L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.INITIATOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanRejectPayments() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/reject", 10L)
                        .sessionAttr("userId", 3L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void initiatorCannotViewAuditLog() throws Exception {
        mockMvc.perform(get("/api/audit")
                        .sessionAttr("userId", 1L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.INITIATOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void attestantCanViewAuditLog() throws Exception {
        when(auditService.getAuditEntries(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/audit")
                        .sessionAttr("userId", 2L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.ATTESTANT))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanViewAuditLog() throws Exception {
        when(auditService.getAuditEntries(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/audit")
                        .sessionAttr("userId", 3L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void attestantCannotViewPaymentAuditTimeline() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentId}/audit", 100L)
                        .sessionAttr("userId", 2L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.ATTESTANT))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanViewPaymentAuditTimeline() throws Exception {
        when(auditService.getPaymentAuditTimeline(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/payments/{paymentId}/audit", 100L)
                        .sessionAttr("userId", 3L)
                        .sessionAttr("tenantId", 1L)
                        .sessionAttr("role", Role.ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestIsForbidden() throws Exception {
        mockMvc.perform(get("/api/approvals"))
                .andExpect(status().isForbidden());
    }
}
