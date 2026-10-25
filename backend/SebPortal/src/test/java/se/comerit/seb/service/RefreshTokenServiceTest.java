package se.comerit.seb.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.RefreshToken;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.RefreshTokenRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.service.RefreshTokenService.RotatedSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// ADR 0012: logiken för refresh-token prövas mot RIKTIGA repositories (H2 i minnet) i stället för
// mockar, eftersom det som ska bevisas är hela kedjan: rotation, återanvändning och att hela
// familjen spärras. Tiden styrs med en egen klocka så att utgång går att testa utan att vänta.
//
// Obs: ogiltiga förnyelser returnerar tomt i stället för att kasta undantag, just för att
// transaktionen inte ska rullas tillbaka och ångra spärren. Testerna här körs i en yttre
// testtransaktion, så själva commit-beteendet visas inte; det finns inget undantag som kan ångra den.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RefreshTokenServiceTest {

    private static final Instant START = Instant.parse("2026-10-12T10:00:00Z");
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    @Autowired
    private RefreshTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private MutableClock clock;
    private RefreshTokenService service;
    private User lisa;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        service = new RefreshTokenService(tokens, users, SEVEN_DAYS, clock);
        lisa = users.saveAndFlush(new User(1L, "Lisa Persson", "lisa@test.se", "hash", Role.INITIATOR));
    }

    // ---- utfärdande ----

    @Test
    void issueForLogin_returnsAnOpaqueUrlSafeToken_andStoresOnlyItsHash() {
        String raw = service.issueForLogin(lisa.getId());

        // 32 slumpade byte blir 43 tecken URL-säker base64
        assertTrue(raw.matches("[A-Za-z0-9_-]{43}"), "token ska vara 43 URL-säkra tecken: " + raw);

        RefreshToken row = stored(raw);
        assertEquals(RefreshTokenService.hash(raw), row.getTokenHash());
        assertEquals(64, row.getTokenHash().length());
        assertNotEquals(raw, row.getTokenHash());
        // Själva värdet finns inte i databasen, bara hashen
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, raw));

        assertEquals(lisa.getId(), row.getUserId());
        assertEquals(at(START.plus(SEVEN_DAYS)), row.getExpiresAt());
        assertFalse(row.isUsed());
        assertFalse(row.isRevoked());
    }

    @Test
    void issueForLogin_startsANewFamilyPerLogin_soSeveralSessionsCanCoexist() {
        String firstLogin = service.issueForLogin(lisa.getId());
        String secondLogin = service.issueForLogin(lisa.getId());

        assertNotEquals(firstLogin, secondLogin);
        assertNotEquals(stored(firstLogin).getFamilyId(), stored(secondLogin).getFamilyId());
        assertTrue(service.rotate(firstLogin).isPresent());
        assertTrue(service.rotate(secondLogin).isPresent());
    }

    @Test
    void issueForLogin_purgesTheUsersExpiredAndRevokedTokens_butNotOthers() {
        RefreshToken expired = tokens.saveAndFlush(new RefreshToken(
                lisa.getId(), "e".repeat(64), UUID.randomUUID(), at(START).minusSeconds(1)));
        RefreshToken revoked = tokens.saveAndFlush(new RefreshToken(
                lisa.getId(), "r".repeat(64), UUID.randomUUID(), at(START).plusDays(3)));
        jdbc.update("update refresh_tokens set revoked_at = ? where id = ?", at(START), revoked.getId());
        RefreshToken otherUsers = tokens.saveAndFlush(new RefreshToken(
                99L, "o".repeat(64), UUID.randomUUID(), at(START).minusDays(1)));

        service.issueForLogin(lisa.getId());

        entityManager.flush();
        entityManager.clear();
        assertTrue(tokens.findById(expired.getId()).isEmpty());
        assertTrue(tokens.findById(revoked.getId()).isEmpty());
        assertTrue(tokens.findById(otherUsers.getId()).isPresent());
        assertEquals(2, tokens.count()); // den andra användarens rad och den nya token
    }

    // ---- rotation ----

    @Test
    void rotate_swapsTheTokenForANewOneInTheSameFamily_andTheSessionSlides() {
        String first = service.issueForLogin(lisa.getId());
        UUID family = stored(first).getFamilyId();
        clock.advance(Duration.ofDays(1));

        Optional<RotatedSession> rotated = service.rotate(first);

        assertTrue(rotated.isPresent());
        String second = rotated.get().refreshToken();
        assertNotEquals(first, second);
        assertEquals(lisa.getId(), rotated.get().user().getId());

        RefreshToken oldRow = stored(first);
        assertTrue(oldRow.isUsed());
        assertEquals(at(START.plus(Duration.ofDays(1))), oldRow.getUsedAt());

        RefreshToken newRow = stored(second);
        assertEquals(family, newRow.getFamilyId());
        assertFalse(newRow.isUsed());
        // Glidande: den nya token gäller 7 dagar från FÖRNYELSEN, inte från inloggningen
        assertEquals(at(START.plus(Duration.ofDays(1)).plus(SEVEN_DAYS)), newRow.getExpiresAt());
    }

    @Test
    void rotate_canBeChained_soAnActiveUserStaysLoggedInPastTheFirstTokensLifetime() {
        String token = service.issueForLogin(lisa.getId());

        // Sex förnyelser med sex dagars mellanrum: sammanlagt 36 dagar, men aldrig längre än 7 mellan
        for (int i = 0; i < 6; i++) {
            clock.advance(Duration.ofDays(6));
            Optional<RotatedSession> rotated = service.rotate(token);
            assertTrue(rotated.isPresent(), "förnyelse " + (i + 1) + " ska lyckas");
            token = rotated.get().refreshToken();
        }
    }

    // ---- ogiltiga token ----

    @Test
    void rotate_withUnknownOrBlankToken_returnsEmpty_andCreatesNothing() {
        assertTrue(service.rotate("finns-inte").isEmpty());
        assertTrue(service.rotate("").isEmpty());
        assertTrue(service.rotate("   ").isEmpty());
        assertTrue(service.rotate(null).isEmpty());

        assertEquals(0, tokens.count());
    }

    @Test
    void rotate_withExpiredToken_returnsEmpty() {
        String token = service.issueForLogin(lisa.getId());
        clock.advance(SEVEN_DAYS.plusSeconds(1));

        assertTrue(service.rotate(token).isEmpty());

        // En utgången token är inte stulen, bara gammal: ingenting markeras eller spärras
        RefreshToken row = stored(token);
        assertFalse(row.isUsed());
        assertFalse(row.isRevoked());
    }

    // ---- återanvändning ----

    @Test
    void rotate_withAnAlreadyUsedToken_revokesTheWholeFamily() {
        String first = service.issueForLogin(lisa.getId());
        String second = service.rotate(first).orElseThrow().refreshToken();

        // Någon visar den gamla, redan förbrukade token igen: den antas vara stulen
        assertTrue(service.rotate(first).isEmpty());

        // Därmed är även den riktiga efterföljaren oanvändbar...
        assertTrue(service.rotate(second).isEmpty());

        // ...och varje rad i familjen är spärrad
        entityManager.flush();
        entityManager.clear();
        List<RefreshToken> family = tokens.findAll();
        assertEquals(2, family.size());
        family.forEach(row -> assertNotNull(row.getRevokedAt()));
    }

    @Test
    void rotate_withAReusedToken_doesNotTouchOtherSessionsOfTheSameUser() {
        String laptop = service.issueForLogin(lisa.getId());
        String phone = service.issueForLogin(lisa.getId());
        service.rotate(laptop);

        assertTrue(service.rotate(laptop).isEmpty()); // återanvändning på laptop

        // Telefonens separata inloggning (egen familj) påverkas inte
        assertTrue(service.rotate(phone).isPresent());
    }

    // ---- utloggning ----

    @Test
    void revoke_endsTheSession_isIdempotent_andLeavesOtherSessionsAlone() {
        String thisSession = service.issueForLogin(lisa.getId());
        String otherSession = service.issueForLogin(lisa.getId());

        service.revoke(thisSession);

        assertTrue(service.rotate(thisSession).isEmpty());
        // Samma anrop igen, en okänd token och en saknad token ger inga fel och ändrar inget
        service.revoke(thisSession);
        service.revoke("finns-inte");
        service.revoke(null);
        service.revoke("   ");

        assertTrue(service.rotate(otherSession).isPresent());
    }

    // ---- användaren ----

    @Test
    void rotate_whenTheUserNoLongerExists_revokesTheFamilyAndReturnsEmpty() {
        String token = service.issueForLogin(9999L); // ingen användare med id 9999

        assertTrue(service.rotate(token).isEmpty());

        assertNotNull(stored(token).getRevokedAt());
    }

    @Test
    void rotate_readsTheUserAgain_soRoleChangesApplyAtTheNextRefresh() {
        String token = service.issueForLogin(lisa.getId());
        assertEquals(Role.INITIATOR, lisa.getRole());

        // Användaren görs till admin direkt i databasen medan sessionen lever
        jdbc.update("update users set role = 'admin' where id = ?", lisa.getId());
        entityManager.clear(); // som i en ny förfrågan: ingen gammal kopia i minnet

        RotatedSession rotated = service.rotate(token).orElseThrow();

        assertEquals(Role.ADMIN, rotated.user().getRole());
    }

    // ---- granskningen av ADR 0012: familjer som redan har en spärrad token ----

    // En aktiv token i en familj som redan har en spärrad token ska stoppas och spärras, även om den
    // själv varken är använd eller spärrad. Det fångar en efterföljare som skapades samtidigt som spärren.
    @Test
    void rotate_withAnActiveTokenInAFamilyThatAlreadyHasARevokedToken_isRejectedAndRevoked() {
        givenAFamilyWithOneRevokedAndOneActiveToken();

        assertTrue(service.rotate("active-raw").isEmpty());

        assertNotNull(stored("active-raw").getRevokedAt());
    }

    // Städningen vid en ny inloggning får inte ta bort det som avslöjar den kvarlämnade aktiva token.
    // Utan den regeln försvinner markören, och token går igen att använda direkt efter inloggningen.
    @Test
    void issueForLogin_doesNotWipeTheRevokedMarkerOfAFamilyThatStillHasALiveToken() {
        givenAFamilyWithOneRevokedAndOneActiveToken();

        service.issueForLogin(lisa.getId());

        assertTrue(service.rotate("active-raw").isEmpty());
    }

    // ---- konfiguration ----

    @Test
    void sessionLifetime_mustBeAtLeastOneDay() {
        assertThrows(IllegalStateException.class,
                () -> new RefreshTokenService(tokens, users, Duration.ZERO, clock));
        assertThrows(IllegalStateException.class,
                () -> new RefreshTokenService(tokens, users, Duration.ofDays(-1), clock));
        assertEquals(SEVEN_DAYS, service.sessionLifetime());
    }

    // ---- hjälpmetoder ----

    // Ett tillstånd som aldrig ska uppstå när allt går rätt till, men som försvaret på djupet ska hantera:
    // en spärrad token och en aktiv token i samma familj. Token-värdena är kända så att de går att använda.
    private void givenAFamilyWithOneRevokedAndOneActiveToken() {
        UUID family = UUID.randomUUID();
        RefreshToken revoked = tokens.saveAndFlush(new RefreshToken(
                lisa.getId(), RefreshTokenService.hash("revoked-raw"), family, at(START).plusDays(3)));
        jdbc.update("update refresh_tokens set revoked_at = ? where id = ?", at(START), revoked.getId());
        tokens.saveAndFlush(new RefreshToken(
                lisa.getId(), RefreshTokenService.hash("active-raw"), family, at(START).plusDays(7)));
    }

    // Läser raden direkt ur databasen (tömmer persistence context först) så att testet ser det som
    // faktiskt sparats och inte en kopia i minnet.
    private RefreshToken stored(String rawToken) {
        entityManager.flush();
        entityManager.clear();
        return tokens.findByTokenHashForUpdate(RefreshTokenService.hash(rawToken)).orElseThrow();
    }

    private static LocalDateTime at(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    // Klocka som testet kan flytta framåt
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
