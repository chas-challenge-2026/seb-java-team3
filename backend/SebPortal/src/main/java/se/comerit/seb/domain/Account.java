package se.comerit.seb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Vilket företag (tenant) kontot tillhör. Kolumnen finns redan i
    // databasen (V2__create_accounts.sql), vi mappar den bara till Java.
    @Column(name = "tenant_id")
    private Long tenantId;

    // NYTT (#184): kolumnerna finns redan i V2__create_accounts.sql,
    // så ingen migrering behövs.
    @Column(name = "account_name")
    private String accountName;

    @Column(name = "iban")
    private String iban;

    @Column(name = "currency")
    private String currency;

    @Column(name = "balance", precision = 15, scale = 2)
    private BigDecimal balance;

    protected Account() {
    }

    public Integer getId() {
        return id;
    }

    // Bara getter, ingen setter, ett konto ska aldrig byta företag.
    public Long getTenantId() {
        return tenantId;
    }

    // NYTT (#184): bara getters. Namn, IBAN och valuta ändras inte via API:t.
    public String getAccountName() {
        return accountName;
    }

    public String getIban() {
        return iban;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}