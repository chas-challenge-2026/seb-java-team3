package se.comerit.seb.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se.comerit.seb.dto.AccountResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.AccountService;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
public class AccountController {

    private final AccountService accountService;
    private final JwtUserContext jwtUserContext;

    public AccountController(AccountService accountService,
                             JwtUserContext jwtUserContext) {
        this.accountService = accountService;
        this.jwtUserContext = jwtUserContext;
    }

    @PreAuthorize("hasAnyRole('INITIATOR', 'ADMIN')")
    @GetMapping("/api/accounts")
    public List<AccountResponse> getAccounts() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return accountService.getAccounts(user);
    }
}