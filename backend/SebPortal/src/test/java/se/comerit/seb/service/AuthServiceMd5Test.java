package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;

// BUG-002: inloggningen jämför bara mot BCrypt, och det finns ingen MD5-fallback. En användare
// som av någon anledning har kvar en gammal MD5-hash ska därför nekas, även med rätt lösenord.
// Kontrolltestet visar att samma lösenord fungerar när hashen är BCrypt, så att testet inte
// blir grönt bara för att inloggningen alltid misslyckas.
@DataJpaTest
@Import(AuthService.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthServiceMd5Test {

    private static final String PASSWORD = "password123";
    // MD5("password123"), samma värde som seeden hade före V7
    private static final String MD5_HASH = "482c811da5d5b4bc6d497ffa98491e38";

    private static final String MD5_EMAIL = "md5@seb.test";
    private static final String BCRYPT_EMAIL = "bcrypt@seb.test";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void seedUsers() {
        String bcryptHash = new BCryptPasswordEncoder().encode(PASSWORD);
        userRepository.save(new User(1L, "MD5 Användare", MD5_EMAIL, MD5_HASH, Role.INITIATOR));
        userRepository.save(new User(1L, "BCrypt Användare", BCRYPT_EMAIL, bcryptHash, Role.INITIATOR));
        userRepository.flush();
    }

    @Test
    void controlCase_bcryptHashWithSamePasswordAuthenticates() {
        assertTrue(authService.authenticate(BCRYPT_EMAIL, PASSWORD).isPresent());
    }

    @Test
    void md5Hash_isRejectedEvenWithCorrectPassword() {
        assertTrue(authService.authenticate(MD5_EMAIL, PASSWORD).isEmpty());
    }

    @Test
    void md5Hash_cannotBeUsedAsPassword() {
        // Den som har kommit över hashen ska inte kunna logga in genom att skicka den som lösenord
        assertTrue(authService.authenticate(MD5_EMAIL, MD5_HASH).isEmpty());
    }
}
