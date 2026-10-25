package se.comerit.seb.dto;

import se.comerit.seb.domain.Account;

import java.math.BigDecimal;

public record AccountResponse(
        Integer id,
        String name,
        String iban,
        BigDecimal balance,
        String currency
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountName(),
                account.getIban(),
                account.getBalance(),
                account.getCurrency()
        );
    }
}