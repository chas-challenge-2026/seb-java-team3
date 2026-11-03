package se.comerit.seb.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.comerit.seb.domain.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    // HS256 kräver en nyckel på minst 256 bit = 32 byte (RFC 7518, avsnitt 3.2).
    private static final int MIN_SECRET_BYTES = 32;

    // Den tidigare dev-hemligheten i application.properties. Den har legat i det publika repot
    // (och ligger kvar i git-historiken), så vem som helst kan skapa giltiga token med den.
    // Därför avvisas den om någon sätter den igen, till exempel via en gammal JWT_SECRET.
    // Paketsynlig så att testet kan använda exakt samma värde.
    static final String COMPROMISED_DEV_SECRET =
            "dev-only-not-for-production-3f8a1c9e6b2d4f7a0c5e8b1d4f7a0c3e6b9d2f5a8c1e4b7d0f3a6c9e2b5d8f1a";

    private final SecretKey key;
    private final long expirationMs;

    // jwt.secret har inget standardvärde i application.properties (se ADR 0011):
    //  - tomt eller saknas: nyckeln slumpas vid start och finns bara i minnet
    //  - satt (till exempel via miljövariabeln JWT_SECRET): används som den är, men bara om den
    //    är minst 32 byte och inte är den läckta dev-hemligheten
    public JwtService(@Value("${jwt.secret:}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = createKey(secret);
        this.expirationMs = expirationMs;
    }

    private static SecretKey createKey(String secret) {
        if (secret == null || secret.isBlank()) {
            // Ingen hemlighet i repot, ingen hemlighet i någon konfigurationsfil: en angripare har
            // inget att räkna ut en signatur med. Priset är att alla token blir ogiltiga vid omstart.
            log.info("jwt.secret är inte satt: en slumpad signeringsnyckel skapas och finns bara i "
                    + "minnet. Alla inloggningar (token) blir ogiltiga vid omstart.");
            return Jwts.SIG.HS256.key().build();
        }

        if (COMPROMISED_DEV_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "jwt.secret är den gamla dev-hemligheten som har legat publikt i repot. "
                            + "Ta bort värdet (då slumpas en nyckel) eller sätt en egen hemlighet "
                            + "på minst " + MIN_SECRET_BYTES + " byte.");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret måste vara minst " + MIN_SECRET_BYTES + " byte (256 bit) för HS256, "
                            + "men är " + secretBytes.length + " byte.");
        }

        return Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(AuthenticatedUserContext user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.userId().toString())
                .claim("tenantId", user.tenantId())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public AuthenticatedUserContext parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.valueOf(claims.getSubject());
        Long tenantId = claims.get("tenantId", Long.class);
        Role role = Role.valueOf(claims.get("role", String.class));

        return new AuthenticatedUserContext(userId, tenantId, role);
    }
}
