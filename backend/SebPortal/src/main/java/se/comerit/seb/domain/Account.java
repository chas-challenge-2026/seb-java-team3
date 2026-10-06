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

    // NYTT: vilket företag (tenant) kontot tillhör. Kolumnen finns redan i
    // databasen (V2__create_accounts.sql), vi mappar den bara till Java.
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "balance", precision = 15, scale = 2)
    private BigDecimal balance;

    protected Account() {
    }

    public Integer getId() {
        return id;
    }

    // NYTT: bara getter, ingen setter, ett konto ska aldrig byta företag.
    public Long getTenantId() {
        return tenantId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}