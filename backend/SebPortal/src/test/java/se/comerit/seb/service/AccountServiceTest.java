package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.domain.Role;
import se.comerit.seb.dto.AccountResponse;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// #186: AccountService körs mot RIKTIGT repository och en RIKTIG SQL-motor (H2 i minnet),
// inte mot en mockad service. Samma upplägg som AccountRepositoryTenantTest.
// Kontrollfallen (eget företags konton kommer med) står först: utan dem kunde "annat
// företags konton returneras inte" vara grönt bara för att tabellen är tom.
@DataJpaTest
@Import(AccountService.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void seedAccounts() {
        // Account har inga setters (ett konto byter aldrig företag), så raderna läggs in med SQL.
        // Raderna rullas tillbaka efter varje test. Tenant B läggs in FÖRST, så att sorteringen
        // på id inte råkar bli rätt bara av att tabellen redan är sorterad.
        insertAccount(TENANT_B, "Företag B Driftkonto", "SE0000000000000000000002", "2000.00");
        insertAccount(TENANT_A, "Företag A Projektkonto", "SE0000000000000000000011", "500.00");
        insertAccount(TENANT_A, "Företag A Driftkonto", "SE0000000000000000000010", "1000.00");
    }

    private void insertAccount(Long tenantId, String name, String iban, String balance) {
        jdbc.update(
                "insert into accounts (tenant_id, account_name, iban, balance, currency) values (?, ?, ?, ?, 'SEK')",
                tenantId, name, iban, new BigDecimal(balance));
    }

    @Test
    void getAccounts_returnsOwnTenantsAccountsWithAllFields() {
        List<AccountResponse> accounts = accountService.getAccounts(user(TENANT_A));

        assertEquals(2, accounts.size());
        AccountResponse first = accounts.get(0);
        assertEquals("Företag A Projektkonto", first.name());
        assertEquals("SE0000000000000000000011", first.iban());
        assertEquals(0, new BigDecimal("500.00").compareTo(first.balance()));
        assertEquals("SEK", first.currency());
    }

    @Test
    void getAccounts_neverReturnsAnotherTenantsAccounts() {
        // R-04: företag A ser bara sina egna, och företag B bara sitt eget
        List<AccountResponse> forA = accountService.getAccounts(user(TENANT_A));
        List<AccountResponse> forB = accountService.getAccounts(user(TENANT_B));

        assertTrue(forA.stream().noneMatch(a -> a.name().contains("Företag B")));
        assertEquals(1, forB.size());
        assertEquals("Företag B Driftkonto", forB.get(0).name());
    }

    @Test
    void getAccounts_isSortedById() {
        List<AccountResponse> accounts = accountService.getAccounts(user(TENANT_A));

        assertEquals(2, accounts.size());
        assertTrue(accounts.get(0).id() < accounts.get(1).id());
    }

    @Test
    void getAccounts_forTenantWithoutAccounts_returnsEmptyList() {
        assertTrue(accountService.getAccounts(user(99L)).isEmpty());
    }

    @Test
    void getAccounts_withoutTenant_isRejectedInsteadOfMatchingAccountsWithoutTenant() {
        // Spring Data översätter ett null-argument till "tenant_id IS NULL". Servicen måste
        // därför avvisa en saknad tenant innan frågan körs.
        jdbc.update("insert into accounts (tenant_id, account_name, balance) values (null, 'Utan företag', 1.00)");

        assertThrows(IllegalStateException.class, () -> accountService.getAccounts(user(null)));
    }

    // Reservation: betalningar som väntar på attest håller sina pengar. Körs mot H2, så att summafrågorna i
    // PaymentRepository prövas på riktigt: bara väntande betalningar räknas, och bara per konto och företag.
    @Test
    void getAccounts_reservesPendingPaymentsAndShowsWhatIsAvailable() {
        Long driftkonto = accountId("SE0000000000000000000010");
        Long otherCompanysAccount = accountId("SE0000000000000000000002");
        payment(TENANT_A, driftkonto, "300.00", PaymentStatus.PENDING_APPROVAL);
        payment(TENANT_A, driftkonto, "200.00", PaymentStatus.PENDING_APPROVAL);
        payment(TENANT_A, driftkonto, "100.00", PaymentStatus.COMPLETED);
        payment(TENANT_A, driftkonto, "50.00", PaymentStatus.REJECTED);
        payment(TENANT_B, otherCompanysAccount, "900.00", PaymentStatus.PENDING_APPROVAL);

        AccountResponse account = accountService.getAccounts(user(TENANT_A)).stream()
                .filter(candidate -> candidate.name().equals("Företag A Driftkonto"))
                .findFirst()
                .orElseThrow();

        assertEquals(0, new BigDecimal("1000.00").compareTo(account.balance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(account.reserved()));
        assertEquals(0, new BigDecimal("500.00").compareTo(account.availableBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(
                paymentRepository.sumPendingAmountForAccount(TENANT_A, driftkonto)));
    }

    @Test
    void getAccounts_withoutPendingPayments_hasEverythingAvailable() {
        AccountResponse account = accountService.getAccounts(user(TENANT_A)).get(0);

        assertEquals(0, BigDecimal.ZERO.compareTo(account.reserved()));
        assertEquals(0, account.balance().compareTo(account.availableBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                paymentRepository.sumPendingAmountForAccount(TENANT_A, accountId("SE0000000000000000000011"))));
    }

    private Long accountId(String iban) {
        return jdbc.queryForObject("select id from accounts where iban = ?", Long.class, iban);
    }

    private void payment(Long tenantId, Long fromAccountId, String amount, PaymentStatus status) {
        Payment payment = new Payment(tenantId, fromAccountId, "SE4550000000058398257466",
                new BigDecimal(amount), "Test", 1L);
        payment.setStatus(status);
        entityManager.persist(payment);
        entityManager.flush();
    }

    private static AuthenticatedUserContext user(Long tenantId) {
        return new AuthenticatedUserContext(1L, tenantId, Role.INITIATOR);
    }
}