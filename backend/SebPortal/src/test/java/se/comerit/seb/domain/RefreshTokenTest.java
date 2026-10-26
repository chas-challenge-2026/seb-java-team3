package se.comerit.seb.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 14, 12, 0, 0);

    private static RefreshToken newToken() {
        return new RefreshToken(1L, "a".repeat(64), UUID.randomUUID(), EXPIRES_AT);
    }

    // Gränsen ska vara samma som i städfrågan (expiresAt < now): på själva tidpunkten är token inte
    // utgången, en nanosekund senare är den det.
    @Test
    void isExpired_isTrueOnlyAfterTheExpiryTime() {
        RefreshToken token = newToken();

        assertFalse(token.isExpired(EXPIRES_AT.minusSeconds(1)));
        assertFalse(token.isExpired(EXPIRES_AT));
        assertTrue(token.isExpired(EXPIRES_AT.plusNanos(1)));
    }

    @Test
    void markUsed_recordsWhenTheTokenWasRotated() {
        RefreshToken token = newToken();
        assertFalse(token.isUsed());

        LocalDateTime usedAt = EXPIRES_AT.minusDays(3);
        token.markUsed(usedAt);

        assertTrue(token.isUsed());
        assertEquals(usedAt, token.getUsedAt());
        assertFalse(token.isRevoked());
    }
}
