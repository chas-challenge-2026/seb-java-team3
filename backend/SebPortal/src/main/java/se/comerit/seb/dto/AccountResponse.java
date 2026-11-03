package se.comerit.seb.dto;

import se.comerit.seb.domain.Account;

import java.math.BigDecimal;

// reserved: summan av betalningar från kontot som väntar på attest.
// availableBalance: saldo minus reserved, det som går att betala med just nu.
public record AccountResponse(
        Integer id,
        String name,
        String iban,
        BigDecimal balance,
        BigDecimal reserved,
        BigDecimal availableBalance,
        String currency
) {
    public static AccountResponse from(Account account, BigDecimal reserved) {
        BigDecimal balance = account.getBalance();
        return new AccountResponse(
                account.getId(),
                account.getAccountName(),
                account.getIban(),
                balance,
                reserved,
                balance == null ? null : balance.subtract(reserved),
                account.getCurrency()
        );
    }
}
