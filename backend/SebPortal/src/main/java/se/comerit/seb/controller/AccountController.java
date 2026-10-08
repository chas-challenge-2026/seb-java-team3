package se.comerit.seb.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se.comerit.seb.dto.AccountResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.AccountService;

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

    @GetMapping("/api/accounts")
    public List<AccountResponse> getAccounts() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return accountService.getAccounts(user);
    }
}