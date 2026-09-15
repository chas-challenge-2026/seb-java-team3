package se.comerit.seb.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import se.comerit.seb.domain.Role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET_A =
            "test-secret-a-must-be-at-least-32-bytes-long-for-hmac-sha256";
    private static final String SECRET_B =
            "test-secret-b-a-completely-different-key-of-sufficient-length";

    @Test
    void generateAndParseToken_shouldRoundTripUserIdentity() {
        JwtService jwtService = new JwtService(SECRET_A, 60_000);
        AuthenticatedUserContext original = new AuthenticatedUserContext(42L, 7L, Role.ATTESTANT);

        String token = jwtService.generateToken(original);
        AuthenticatedUserContext parsed = jwtService.parseToken(token);

        assertEquals(original, parsed);
    }

    @Test
    void parseToken_shouldRejectExpiredToken() {
        // Negativt expiration-ms => "expiration" hamnar i det förflutna direkt vid utfärdande.
        JwtService jwtService = new JwtService(SECRET_A, -1_000);
        String token = jwtService.generateToken(new AuthenticatedUserContext(1L, 1L, Role.ADMIN));

        assertThrows(ExpiredJwtException.class, () -> jwtService.parseToken(token));
    }

    @Test
    void parseToken_shouldRejectTokenSignedWithDifferentSecret() {
        JwtService issuer = new JwtService(SECRET_A, 60_000);
        JwtService verifier = new JwtService(SECRET_B, 60_000);
        String token = issuer.generateToken(new AuthenticatedUserContext(1L, 1L, Role.INITIATOR));

        assertThrows(SignatureException.class, () -> verifier.parseToken(token));
    }

    @Test
    void parseToken_shouldRejectMalformedToken() {
        JwtService jwtService = new JwtService(SECRET_A, 60_000);

        assertThrows(MalformedJwtException.class, () -> jwtService.parseToken("not-a-real-token"));
    }
}
