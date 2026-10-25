package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.dto.AccountResponse;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    // Tenant kommer ALLTID från den inloggade användaren (JWT), aldrig från requesten.
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(AuthenticatedUserContext user) {
        if (user.tenantId() == null) {
            throw new IllegalStateException("Inloggad användare saknar företag");
        }

        return accountRepository.findByTenantIdOrderByIdAsc(user.tenantId())
                .stream()
                .map(AccountResponse::from)
                .toList();
    }
}