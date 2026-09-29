package se.comerit.seb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.MyPaymentStatusResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.AuditService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditControllerMyPaymentsTest {

    @Test
    void getMyPaymentStatuses_shouldReturnOnlyOwnPayments() throws Exception {
        AuditService auditService = mock(AuditService.class);
        JwtUserContext jwtUserContext = mock(JwtUserContext.class);
        AuditController controller = new AuditController(auditService, jwtUserContext);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        AuthenticatedUserContext initiator = new AuthenticatedUserContext(1L, 1L, Role.INITIATOR);
        MyPaymentStatusResponse ownPayment = new MyPaymentStatusResponse(
                100L, "Testbetalning", new BigDecimal("500.00"), "SEK",
                "SE1234567890123456789012", "PENDING_APPROVAL", null, null);

        when(jwtUserContext.requireAuthenticated()).thenReturn(initiator);
        when(auditService.getMyPaymentStatuses(initiator)).thenReturn(List.of(ownPayment));

        mockMvc.perform(get("/api/my-payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value(100))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }
}
