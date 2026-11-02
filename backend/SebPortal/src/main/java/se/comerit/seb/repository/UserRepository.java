package se.comerit.seb.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Exakt träff. Anropare normaliserar först med EmailAddress.normalize, eftersom alla e-postadresser
    // sparas i den formen.
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Sorterad, så att tilldelningen av attestanter inte beror på i vilken ordning databasen råkar ge raderna.
    List<User> findByTenantIdAndRoleOrderByIdAsc(Long tenantId, Role role);

    // Företagets användare, sorterade på namn (och id när två har samma namn) så att listan blir stabil.
    // Företaget måste komma från den inloggade användarens token och får aldrig vara null: Spring Data
    // översätter ett null-argument till "tenant_id IS NULL" och skulle då lista användare utan företag.
    List<User> findByTenantIdOrderByNameAscIdAsc(Long tenantId);

    // Användaren BARA om hen tillhör företaget, och raden låst (SELECT ... FOR UPDATE) tills transaktionen är
    // klar. Används när en admin byter roll: ett annat företags användare ser ut som en användare som inte
    // finns, och ingen annans rad låses. Ett null-företag matchar aldrig (SQL: tenant_id = NULL är aldrig sant).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM User u
            WHERE u.id = :userId
              AND u.tenantId = :tenantId
            """)
    Optional<User> findByIdAndTenantIdForUpdate(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    // Låser användarens rad (SELECT ... FOR UPDATE) tills transaktionen är klar. Används som ett
    // lås per användare: RefreshTokenService tar det först i inloggning, förnyelse och utloggning,
    // så att två sådana operationer för samma användare aldrig överlappar (ADR 0012). Utan det kan
    // en spärr av en token-familj missa en efterföljare som skapas samtidigt.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM User u
            WHERE u.id = :userId
            """)
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    // Hämtar när användaren senast markerade sina betalningar som sedda.
    // Tomt resultat betyder att användaren aldrig gjort det.
    @Query("""
            SELECT u.paymentsSeenAt
            FROM User u
            WHERE u.id = :id AND u.tenantId = :tenantId
            """)
    Optional<LocalDateTime> findPaymentsSeenAt(@Param("id") Long id,
                                               @Param("tenantId") Long tenantId);

    // Sätter "sedd"-tidpunkten med en UPDATE, så att inga andra fält på användaren skrivs över.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.paymentsSeenAt = :now
            WHERE u.id = :id AND u.tenantId = :tenantId
            """)
    int markPaymentsSeen(@Param("id") Long id,
                         @Param("tenantId") Long tenantId,
                         @Param("now") LocalDateTime now);
}
