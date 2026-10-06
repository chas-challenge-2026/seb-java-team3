package se.comerit.seb.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import se.comerit.seb.domain.Role;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // ---- Signeringsnyckeln (ADR 0011): ingen hemlighet i repot, slumpad nyckel som standard ----

    // Saknad, tom eller bara blanksteg i jwt.secret ska ge en fungerande, slumpad nyckel i stället för
    // ett startfel: stage kan inte få hemligheter injicerade (DRIFT.md), så appen måste starta ändå.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankSecret_shouldGenerateAWorkingRandomKey(String blankSecret) {
        JwtService jwtService = new JwtService(blankSecret, 60_000);
        AuthenticatedUserContext original = new AuthenticatedUserContext(42L, 7L, Role.ATTESTANT);

        AuthenticatedUserContext parsed = jwtService.parseToken(jwtService.generateToken(original));

        assertEquals(original, parsed);
    }

    // Varje start får sin egen nyckel. Två instanser motsvarar före och efter en omstart: en token
    // utfärdad före omstarten ska inte gå att använda efter den (priset för att ingen nyckel sparas).
    @Test
    void blankSecret_eachInstanceShouldGetItsOwnKey() {
        JwtService beforeRestart = new JwtService("", 60_000);
        JwtService afterRestart = new JwtService("", 60_000);
        String token = beforeRestart.generateToken(new AuthenticatedUserContext(1L, 1L, Role.INITIATOR));

        assertThrows(SignatureException.class, () -> afterRestart.parseToken(token));
    }

    // Själva hålet som ADR 0011 stänger: den gamla hemligheten stod i det publika repot, så vem som
    // helst kunde bygga en ADMIN-token för valfritt företag. Kontrollfallet först: samma förfalskning
    // FUNGERAR mot en server vars nyckel angriparen känner till. Då vet vi att det är nyckeln, och inte
    // en trasig token, som gör att den slumpade nyckeln nekar.
    @Test
    void tokenForgedWithAKnownSecret_isOnlyAcceptedByAServerUsingThatSecret() throws Exception {
        String forgedWithSecretA = forgeAdminToken(SECRET_A);
        AuthenticatedUserContext forgedIdentity = new AuthenticatedUserContext(999L, 42L, Role.ADMIN);

        // Kontroll: förfalskningen fungerar om servern använder den kända hemligheten
        assertEquals(forgedIdentity, new JwtService(SECRET_A, 60_000).parseToken(forgedWithSecretA));

        // ADR 0011: förfalskad med den gamla, publika hemligheten nekas av en server med slumpad nyckel
        String forgedWithOldPublicSecret = forgeAdminToken(JwtService.COMPROMISED_DEV_SECRET);
        JwtService serverWithRandomKey = new JwtService("", 60_000);

        assertThrows(SignatureException.class, () -> serverWithRandomKey.parseToken(forgedWithOldPublicSecret));
    }

    @Test
    void compromisedDevSecret_shouldBeRejectedAtStartup() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new JwtService(JwtService.COMPROMISED_DEV_SECRET, 60_000));

        assertTrue(exception.getMessage().contains("dev-hemligheten"));
    }

    // HS256 kräver minst 256 bit. Gränsen testas på båda sidor: 31 byte nekas, 32 byte godtas.
    @Test
    void explicitSecret_mustBeAtLeast32Bytes() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new JwtService("a".repeat(31), 60_000));

        assertTrue(exception.getMessage().contains("minst 32 byte"));
        assertDoesNotThrow(() -> new JwtService("a".repeat(32), 60_000));
    }

    // Bygger en token för valfri identitet (användare 999, företag 42, roll ADMIN) med ren HMAC-matematik,
    // utan inloggning och utan jjwt: precis vad en angripare gör när hemligheten är känd.
    private static String forgeAdminToken(String secret) throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        long now = System.currentTimeMillis() / 1000;

        String header = base64.encodeToString("{\"alg\":\"HS256\"}".getBytes(UTF_8));
        String payload = base64.encodeToString((
                "{\"sub\":\"999\",\"tenantId\":42,\"role\":\"ADMIN\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}"
        ).getBytes(UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
        String signature = base64.encodeToString(mac.doFinal((header + "." + payload).getBytes(UTF_8)));

        return header + "." + payload + "." + signature;
    }
}
