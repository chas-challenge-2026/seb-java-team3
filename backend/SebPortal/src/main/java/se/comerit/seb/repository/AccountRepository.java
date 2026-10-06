package se.comerit.seb.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.comerit.seb.domain.Account;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Integer> {

    // Spring Data bygger frågan från metodnamnet:
    // SELECT count(*) > 0 FROM accounts WHERE id = ? AND tenant_id = ?
    // Parametriserad, alltså ingen risk för SQL-injektion.
    boolean existsByIdAndTenantId(Integer id, Long tenantId);

    // Hämtar kontot BARA om det tillhör rätt företag, och låser raden (SELECT ... FOR UPDATE)
    // tills transaktionen är klar. Används när pengar faktiskt flyttas (ApprovalService):
    //  - tenant-villkoret gör att ett annat företags konto aldrig kan hämtas, även om
    //    betalningen råkar peka på det (R-04)
    //  - låset gör att två godkännanden mot samma konto går efter varandra i stället för att
    //    båda läsa samma saldo och skriva över varandras avdrag
    // Frågan är skriven med @Query eftersom ett metodnamn som "...ForUpdate" inte går att
    // härleda; samma upplägg som PaymentRepository.findByApprovalStepIdForUpdate.
    // OBS: en null-tenant matchar aldrig här (SQL: tenant_id = NULL är aldrig sant), till
    // skillnad från existsByIdAndTenantId ovan, som översätter null till "tenant_id IS NULL".
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM Account account
            WHERE account.id = :accountId
              AND account.tenantId = :tenantId
            """)
    Optional<Account> findByIdAndTenantIdForUpdate(
            @Param("accountId") Integer accountId,
            @Param("tenantId") Long tenantId);
}
