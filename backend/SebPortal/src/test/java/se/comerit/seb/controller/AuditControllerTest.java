package se.comerit.seb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.AuditEntryResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.SessionUserContext;
import se.comerit.seb.service.AuditService;
import se.comerit.seb.security.JwtUserContext;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditControllerTest {

        @Test
        void getAuditEntries_shouldReturnCreateAndApproveEvents() throws Exception {
                AuditService auditService = mock(AuditService.class);
                SessionUserContext sessionUserContext = mock(SessionUserContext.class);
                JwtUserContext jwtUserContext = mock(JwtUserContext.class);
                AuditController controller = new AuditController(
                                auditService,
                                sessionUserContext,
                                jwtUserContext);
                MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

                AuthenticatedUserContext admin = new AuthenticatedUserContext(1L, 1L, Role.ADMIN);
                AuditEntryResponse createEvent = new AuditEntryResponse(
                                1L, "CREATE_PAYMENT", "PAYMENT", 100L,
                                "Betalning skapad", "COMPLETED", "Testbetalning", null, "Admin");
                AuditEntryResponse approveEvent = new AuditEntryResponse(
                                2L, "APPROVE_PAYMENT", "PAYMENT", 100L,
                                "Betalning godkänd", "COMPLETED", "Testbetalning", null, "Admin");

                when(sessionUserContext.requireAdminOrAttestant(any())).thenReturn(admin);
                when(auditService.getAuditEntries(admin)).thenReturn(List.of(createEvent, approveEvent));

                MockHttpSession session = new MockHttpSession();

                mockMvc.perform(get("/api/audit").session(session))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[*].action",
                                                containsInAnyOrder("CREATE_PAYMENT", "APPROVE_PAYMENT")));
        }
}
