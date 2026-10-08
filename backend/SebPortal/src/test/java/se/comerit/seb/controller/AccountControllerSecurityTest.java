package se.comerit.seb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.AccountResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.RoleAccessDeniedHandler;
import se.comerit.seb.service.AccountService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #186 / #185: hela kedjan (filter -> JwtSecurityConfig -> @PreAuthorize -> controller) med
// riktiga JWT-token, samma upplägg som ApprovalApiControllerSecurityTest. Servicen är mockad
// här eftersom testet gäller bara behörighet; tenant-filtret mot databas testas i AccountServiceTest.
@WebMvcTest(AccountController.class)
@Import({JwtSecurityConfig.class, JwtService.class, JwtUserContext.class, RoleAccessDeniedHandler.class})
class AccountControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AccountService accountService;

    @Test
    void getAccounts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());

        verify(accountService, never()).getAccounts(any());
    }

    @Test
    void getAccounts_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccounts_asAttestant_returns403() throws Exception {
        mockMvc.perform(get("/api/accounts").header("Authorization", bearer(Role.ATTESTANT)))
                .andExpect(status().isForbidden());

        verify(accountService, never()).getAccounts(any());
    }

    @Test
    void getAccounts_asInitiator_returns200WithAccounts() throws Exception {
        when(accountService.getAccounts(any())).thenReturn(List.of(
                new AccountResponse(1, "Driftkonto", "SE0000000000000000000010",
                        new BigDecimal("1000.00"), "SEK")));

        mockMvc.perform(get("/api/accounts").header("Authorization", bearer(Role.INITIATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Driftkonto"))
                .andExpect(jsonPath("$[0].iban").value("SE0000000000000000000010"))
                .andExpect(jsonPath("$[0].balance").value(1000.00))
                .andExpect(jsonPath("$[0].currency").value("SEK"));
    }

    @Test
    void getAccounts_asAdmin_returns200() throws Exception {
        when(accountService.getAccounts(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/accounts").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    private String bearer(Role role) {
        return "Bearer " + jwtService.generateToken(new AuthenticatedUserContext(1L, 1L, role));
    }
}