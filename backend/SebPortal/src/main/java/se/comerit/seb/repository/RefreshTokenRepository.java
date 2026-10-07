package se.comerit.seb.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.comerit.seb.domain.RefreshToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Slår upp en token via hashen och låser raden (SELECT ... FOR UPDATE) tills transaktionen är klar.
    // Låset gör att två samtidiga förnyelser med SAMMA token går efter varandra: den andra ser att
    // token redan är använd och utlöser återanvändningsdetektionen, i stället för att båda lyckas
    // och ger varsin giltig efterföljare. Samma upplägg som PaymentRepository.findByApprovalStepIdForUpdate.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM RefreshToken token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // Spärrar alla token i en familj (en inloggning) som inte redan är spärrade, och returnerar antalet.
    // Används vid utloggning och när återanvändning upptäcks. Redan spärrade rader rörs inte, så
    // deras ursprungliga tidpunkt finns kvar. clearAutomatically: en bulk-uppdatering går förbi
    // persistence context, så inlästa entiteter måste kastas för att inte visa gamla värden.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken token
            SET token.revokedAt = :now
            WHERE token.familyId = :familyId
              AND token.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now);

    // Städar bort en användares utgångna och spärrade token, och returnerar antalet. Använda men
    // fortfarande giltiga token ligger kvar med flit: de behövs tills de går ut för att kunna upptäcka
    // att någon försöker använda en gammal token igen.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM RefreshToken token
            WHERE token.userId = :userId
              AND (token.expiresAt < :now OR token.revokedAt IS NOT NULL)
            """)
    int deleteExpiredOrRevokedForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
