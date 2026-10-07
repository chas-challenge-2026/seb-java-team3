package se.comerit.seb.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.RefreshToken;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// ADR 0012: frågorna för refresh-token körs mot en RIKTIG SQL-motor (H2 i minnet, samma upplägg som
// AccountRepositoryTenantTest). Schemat genereras här av Hibernate från entiteten; själva Flyway-
// migrationen V8 prövas inte av det här testet utan separat mot Postgres.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RefreshTokenRepositoryTest {

    // Fast tidpunkt utan nanosekunder, så att värden går att jämföra exakt efter en runda genom databasen
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0, 0);
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    // SHA-256 som hex är 64 tecken; testen behöver bara unika värden av rätt längd
    private static String hash(char fill) {
        return String.valueOf(fill).repeat(64);
    }

    private RefreshToken save(Long userId, String tokenHash, UUID familyId, LocalDateTime expiresAt) {
        return repository.saveAndFlush(new RefreshToken(userId, tokenHash, familyId, expiresAt));
    }

    @Test
    void savedToken_roundTripsAllFields() {
        UUID family = UUID.randomUUID();
        RefreshToken saved = save(USER_A, hash('a'), family, NOW.plusDays(7));
        entityManager.clear(); // tvinga fram en riktig läsning ur databasen i stället för cachen

        RefreshToken found = repository.findByTokenHashForUpdate(hash('a')).orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals(USER_A, found.getUserId());
        assertEquals(hash('a'), found.getTokenHash());
        assertEquals(family, found.getFamilyId());
        assertEquals(NOW.plusDays(7), found.getExpiresAt());
        assertNotNull(found.getCreatedAt());
        assertFalse(found.isUsed());
        assertFalse(found.isRevoked());
    }

    @Test
    void findByTokenHashForUpdate_findsNothingForAnUnknownHash() {
        save(USER_A, hash('a'), UUID.randomUUID(), NOW.plusDays(7));

        assertTrue(repository.findByTokenHashForUpdate(hash('z')).isEmpty());
    }

    @Test
    void findByTokenHashForUpdate_locksTheRow() {
        save(USER_A, hash('a'), UUID.randomUUID(), NOW.plusDays(7));
        entityManager.clear();

        RefreshToken token = repository.findByTokenHashForUpdate(hash('a')).orElseThrow();

        // Testet misslyckas om @Lock tas bort: då kunde två samtidiga förnyelser med samma token
        // båda läsa den som oanvänd och ge varsin giltig efterföljare.
        assertEquals(LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(token));
    }

    @Test
    void tokenHash_mustBeUnique() {
        save(USER_A, hash('a'), UUID.randomUUID(), NOW.plusDays(7));

        assertThrows(DataIntegrityViolationException.class,
                () -> save(USER_B, hash('a'), UUID.randomUUID(), NOW.plusDays(7)));
    }

    @Test
    void revokeFamily_revokesEveryTokenInThatFamilyOnly_andOnlyOnce() {
        UUID familyA = UUID.randomUUID();
        UUID familyB = UUID.randomUUID();
        RefreshToken used = save(USER_A, hash('a'), familyA, NOW.plusDays(7));
        RefreshToken active = save(USER_A, hash('b'), familyA, NOW.plusDays(7));
        RefreshToken otherFamily = save(USER_B, hash('c'), familyB, NOW.plusDays(7));
        used.markUsed(NOW.minusMinutes(5));
        repository.saveAndFlush(used);

        int revoked = repository.revokeFamily(familyA, NOW);

        // Båda i familjen spärras, även den som redan var använd. Den andra familjen rörs inte.
        assertEquals(2, revoked);
        RefreshToken reloadedUsed = repository.findById(used.getId()).orElseThrow();
        assertEquals(NOW, reloadedUsed.getRevokedAt());
        assertTrue(reloadedUsed.isUsed());
        assertEquals(NOW, repository.findById(active.getId()).orElseThrow().getRevokedAt());
        assertNull(repository.findById(otherFamily.getId()).orElseThrow().getRevokedAt());

        // Ett andra anrop rör inget och skriver inte över den ursprungliga tidpunkten
        assertEquals(0, repository.revokeFamily(familyA, NOW.plusHours(1)));
        assertEquals(NOW, repository.findById(active.getId()).orElseThrow().getRevokedAt());
    }

    @Test
    void deleteExpiredOrRevokedForUser_keepsUsedButStillValidTokens() {
        UUID family = UUID.randomUUID();
        RefreshToken active = save(USER_A, hash('a'), family, NOW.plusDays(7));
        RefreshToken usedButValid = save(USER_A, hash('b'), family, NOW.plusDays(7));
        usedButValid.markUsed(NOW.minusMinutes(1));
        repository.saveAndFlush(usedButValid);
        RefreshToken expired = save(USER_A, hash('c'), UUID.randomUUID(), NOW.minusSeconds(1));
        RefreshToken revoked = save(USER_A, hash('d'), UUID.randomUUID(), NOW.plusDays(7));
        jdbc.update("update refresh_tokens set revoked_at = ? where id = ?", NOW.minusHours(1), revoked.getId());
        RefreshToken otherUsersExpired = save(USER_B, hash('e'), UUID.randomUUID(), NOW.minusDays(1));

        int deleted = repository.deleteExpiredOrRevokedForUser(USER_A, NOW);

        assertEquals(2, deleted);
        assertTrue(repository.findById(expired.getId()).isEmpty());
        assertTrue(repository.findById(revoked.getId()).isEmpty());
        assertTrue(repository.findById(active.getId()).isPresent());
        // En använd men giltig token måste ligga kvar: den behövs för att upptäcka att någon försöker
        // använda en gammal token igen (återanvändningsdetektionen).
        assertTrue(repository.findById(usedButValid.getId()).isPresent());
        // Bara den angivna användarens rader städas
        assertTrue(repository.findById(otherUsersExpired.getId()).isPresent());
    }
}
