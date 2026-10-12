package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.AccountResponse;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// #186: saldot som /api/accounts visar ska stämma efter en attesterad betalning. Hela kedjan
// körs mot H2: ApprovalService drar saldot, AccountService läser det.
@DataJpaTest
@Import({ApprovalService.class, AccountService.class, AuditService.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountBalanceAfterApprovalTest {

    private static final Long TENANT = 1L;
    private static final Long ATTESTANT_ID = 7L;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Integer accountId;
    private Long approvalStepId;

    @BeforeEach
    void seed() {
        jdbc.update("insert into accounts (tenant_id, account_name, iban, balance, currency) "
                + "values (?, 'Driftkonto', 'SE0000000000000000000010', ?, 'SEK')", TENANT, new BigDecimal("1000.00"));
        accountId = jdbc.queryForObject("select id from accounts where tenant_id = ?", Integer.class, TENANT);

        Payment payment = new Payment(TENANT, accountId.longValue(), "SE0000000000000000000099",
                new BigDecimal("250.00"), "Faktura 1", 1L);
        payment.addApprovalStep(new ApprovalStep(ATTESTANT_ID, 1));
        paymentRepository.saveAndFlush(payment);
        approvalStepId = payment.getApprovalSteps().get(0).getId();
    }

    @Test
    void balanceIsUnchangedBeforeApproval() {
        // Kontrollfall: en väntande betalning drar inget saldo
        assertEquals(0, new BigDecimal("1000.00").compareTo(balanceOfOnlyAccount()));
    }

    @Test
    void balanceIsReducedByTheAmountAfterApproval() {
        approvalService.approve(approvalStepId, ATTESTANT_ID);

        assertEquals(0, new BigDecimal("750.00").compareTo(balanceOfOnlyAccount()));
    }

    private BigDecimal balanceOfOnlyAccount() {
        List<AccountResponse> accounts = accountService.getAccounts(
                new AuthenticatedUserContext(1L, TENANT, Role.INITIATOR));
        assertEquals(1, accounts.size());
        return accounts.get(0).balance();
    }
}