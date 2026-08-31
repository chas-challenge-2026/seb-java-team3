package se.comerit.seb.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createToken(String subject, Map<String, Object> claims) {
        if (!StringUtils.hasText(properties.getSigningKey())) {
            throw new IllegalStateException("JWT signing key is not configured. Set JWT_SIGNING_KEY.");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getExpirationMinutes() * 60);

        return Jwts.builder()
                .setSubject(subject)
                .setIssuer(properties.getIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .addClaims(claims)
                .signWith(Keys.hmacShaKeyFor(properties.getSigningKey().getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
    }
}
