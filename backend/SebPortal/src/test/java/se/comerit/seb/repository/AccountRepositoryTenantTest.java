package se.comerit.seb.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.Account;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// R-04: ett konto får bara kunna nås av det företag som äger det. Servicetesterna mockar
// AccountRepository och kan därför inte visa att SQL-frågorna faktiskt filtrerar på tenant.
// Det här testet kör frågorna mot en RIKTIG SQL-motor (H2 i minnet, samma upplägg som
// AuthServiceInjectionTest) med konton för två olika företag.
// Kontrollfallen (eget konto hittas) står först i varje test: utan dem skulle de negativa
// fallen kunna vara gröna bara för att tabellen är tom.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountRepositoryTenantTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final int UNKNOWN_ACCOUNT_ID = 999_999;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private Integer accountOfA;
    private Integer accountOfB;
    private Integer accountWithoutTenant;

    @BeforeEach
    void seedAccounts() {
        // Account har med flit inga setters för id och tenantId (ett konto byter aldrig företag),
        // så testraderna läggs in med SQL. Raderna rullas tillbaka efter varje test.
        jdbc.update("insert into accounts (tenant_id, balance) values (?, ?)", TENANT_A, new BigDecimal("1000.00"));
        jdbc.update("insert into accounts (tenant_id, balance) values (?, ?)", TENANT_B, new BigDecimal("2000.00"));
        jdbc.update("insert into accounts (tenant_id, balance) values (null, ?)", new BigDecimal("3000.00"));

        accountOfA = jdbc.queryForObject("select id from accounts where tenant_id = ?", Integer.class, TENANT_A);
        accountOfB = jdbc.queryForObject("select id from accounts where tenant_id = ?", Integer.class, TENANT_B);
        accountWithoutTenant = jdbc.queryForObject("select id from accounts where tenant_id is null", Integer.class);
    }

    // ---- existsByIdAndTenantId: kontrollen vid skapande av betalning (#187) ----

    @Test
    void existsByIdAndTenantId_isTrueOnlyForTheOwningTenant() {
        // Kontrollfall: varje företag ser sitt eget konto
        assertTrue(accountRepository.existsByIdAndTenantId(accountOfA, TENANT_A));
        assertTrue(accountRepository.existsByIdAndTenantId(accountOfB, TENANT_B));

        // R-04: annat företags konto nekas, i båda riktningarna
        assertFalse(accountRepository.existsByIdAndTenantId(accountOfB, TENANT_A));
        assertFalse(accountRepository.existsByIdAndTenantId(accountOfA, TENANT_B));

        // Ett konto som inte finns ger samma svar som ett konto hos ett annat företag
        assertFalse(accountRepository.existsByIdAndTenantId(UNKNOWN_ACCOUNT_ID, TENANT_A));
    }

    @Test
    void existsByIdAndTenantId_treatsNullTenantAsIsNull_soCallersMustRejectNullFirst() {
        // Skarp kant som är bra att känna till: Spring Datas härledda fråga översätter ett
        // null-argument till "tenant_id IS NULL" och släpper då igenom konton som saknar
        // företag. Därför avvisar PaymentService en saknad tenantId INNAN frågan körs.
        assertTrue(accountRepository.existsByIdAndTenantId(accountWithoutTenant, null));
        assertFalse(accountRepository.existsByIdAndTenantId(accountOfA, null));
    }

    // ---- findByIdAndTenantIdForUpdate: hämtningen vid godkännande (#191) ----

    @Test
    void findByIdAndTenantIdForUpdate_returnsTheAccountToItsOwningTenant() {
        Optional<Account> found = accountRepository.findByIdAndTenantIdForUpdate(accountOfA, TENANT_A);

        assertTrue(found.isPresent());
        assertEquals(accountOfA, found.get().getId());
        // Visar också att tenant_id (INT i databasen) mappas rätt till Account.tenantId (Long)
        assertEquals(TENANT_A, found.get().getTenantId());
        assertEquals(0, new BigDecimal("1000.00").compareTo(found.get().getBalance()));
    }

    @Test
    void findByIdAndTenantIdForUpdate_findsNothingForAnotherTenantsAccount() {
        // R-04: samma konto-id men fel företag ger ingenting att belasta
        assertTrue(accountRepository.findByIdAndTenantIdForUpdate(accountOfB, TENANT_A).isEmpty());
        assertTrue(accountRepository.findByIdAndTenantIdForUpdate(accountOfA, TENANT_B).isEmpty());
        assertTrue(accountRepository.findByIdAndTenantIdForUpdate(UNKNOWN_ACCOUNT_ID, TENANT_A).isEmpty());
    }

    @Test
    void findByIdAndTenantIdForUpdate_neverMatchesANullTenant() {
        // Till skillnad från existsByIdAndTenantId ovan matchar en null-tenant aldrig här,
        // inte ens mot ett konto som saknar företag.
        assertTrue(accountRepository.findByIdAndTenantIdForUpdate(accountWithoutTenant, null).isEmpty());
        assertTrue(accountRepository.findByIdAndTenantIdForUpdate(accountOfA, null).isEmpty());
    }

    @Test
    void findByIdAndTenantIdForUpdate_locksTheRow() {
        Account account = accountRepository.findByIdAndTenantIdForUpdate(accountOfA, TENANT_A).orElseThrow();

        // Testet misslyckas om @Lock tas bort från repository-metoden: då hade kontot lästs
        // utan lås och två samtidiga godkännanden kunnat skriva över varandras saldo.
        assertEquals(LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(account));
    }
}
