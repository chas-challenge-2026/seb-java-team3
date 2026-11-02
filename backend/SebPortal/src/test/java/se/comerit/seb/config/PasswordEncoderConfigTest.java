package se.comerit.seb.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Den delade lösenordshashningen (PasswordEncoderConfig). Inloggningen och användaradministrationen
// bygger båda på den, så den ska hasha med rätt kostnad och fortfarande känna igen de hashar som redan
// finns i databasen.
class PasswordEncoderConfigTest {

    private static final String PASSWORD = "ett-langt-testlosenord";

    // Seed-hashen från V7 för lösenordet password123 (kostnad 12, prefix $2b$)
    private static final String SEED_HASH = "$2b$12$bY5T.uCVV.y858KslPx/ZOIFx7pfFiFYNKfM1j.kY..i8Qj4vz406";

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void newHashes_useCost12() {
        assertTrue(encoder.encode(PASSWORD).startsWith("$2a$12$"));
    }

    @Test
    void samePassword_getsADifferentHashEachTime_andBothMatch() {
        String first = encoder.encode(PASSWORD);
        String second = encoder.encode(PASSWORD);

        // Varje hash har sitt eget salt, så färdiga uppslagstabeller över vanliga lösenord hjälper inte
        assertNotEquals(first, second);
        assertTrue(encoder.matches(PASSWORD, first));
        assertTrue(encoder.matches(PASSWORD, second));
    }

    @Test
    void wrongPassword_doesNotMatch() {
        assertFalse(encoder.matches(PASSWORD + "x", encoder.encode(PASSWORD)));
    }

    @Test
    void seedHashFromV7_stillMatches() {
        assertTrue(encoder.matches("password123", SEED_HASH));
        assertFalse(encoder.matches("password124", SEED_HASH));
    }

    // Kostnaden ligger i hashen. Användare som fick en hash med Springs gamla standardkostnad (10) ska
    // inte låsas ute av att nya hashar använder 12.
    @Test
    void hashesWithAnotherCost_stillMatch() {
        String cost10 = new BCryptPasswordEncoder(10).encode(PASSWORD);

        assertTrue(cost10.startsWith("$2a$10$"));
        assertTrue(encoder.matches(PASSWORD, cost10));
    }

    @Test
    void aPlainOrMd5Hash_neverMatches() {
        assertFalse(encoder.matches(PASSWORD, PASSWORD));
        assertFalse(encoder.matches("password123", "482c811da5d5b4bc6d497ffa98491e38"));
    }
}
