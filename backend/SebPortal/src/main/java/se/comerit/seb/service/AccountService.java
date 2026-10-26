package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.dto.AccountResponse;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.PaymentRepository.ReservedAmount;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;

    public AccountService(AccountRepository accountRepository, PaymentRepository paymentRepository) {
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
    }

    // Tenant kommer ALLTID från den inloggade användaren (JWT), aldrig från requesten.
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(AuthenticatedUserContext user) {
        if (user.tenantId() == null) {
            throw new IllegalStateException("Inloggad användare saknar företag");
        }

        // Reserverat per konto: betalningar som väntar på attest (se PaymentRepository)
        Map<Long, BigDecimal> reservedByAccount = paymentRepository.sumPendingAmountPerAccount(user.tenantId())
                .stream()
                .collect(Collectors.toMap(ReservedAmount::getAccountId, ReservedAmount::getTotal));

        return accountRepository.findByTenantIdOrderByIdAsc(user.tenantId())
                .stream()
                .map(account -> AccountResponse.from(
                        account,
                        reservedByAccount.getOrDefault(account.getId().longValue(), BigDecimal.ZERO)))
                .toList();
    }
}