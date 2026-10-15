package se.comerit.seb.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import se.comerit.seb.domain.Role;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

// ADR 0012: access-token ska gälla 15 minuter. Frontend förnyar den tyst med refresh-token, så en kort
// livslängd kostar användaren ingenting, men en lång gör att en stulen token lever längre. Testet läser
// den RIKTIGA konfigurationen (application.properties), så att ett oavsiktligt återställt värde märks.
@SpringBootTest(classes = JwtService.class)
class JwtLifetimeConfigTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void accessTokenLivesFifteenMinutesWithTheDefaultConfiguration() throws Exception {
        String token = jwtService.generateToken(new AuthenticatedUserContext(1L, 1L, Role.INITIATOR));

        // Mittendelen i en JWT är innehållet (payload), base64url-kodat
        String payloadJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), UTF_8);
        JsonNode payload = new ObjectMapper().readTree(payloadJson);

        long lifetimeSeconds = payload.get("exp").asLong() - payload.get("iat").asLong();

        assertEquals(15 * 60, lifetimeSeconds);
    }
}
