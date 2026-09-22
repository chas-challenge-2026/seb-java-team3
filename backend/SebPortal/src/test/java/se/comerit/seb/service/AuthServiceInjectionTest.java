package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// R-06: inloggningen går genom JPA (parameteriserad query) + BCrypt. Testet kör mot en RIKTIG
// SQL-motor (H2 i minnet) med en riktig användare, så att ett injektionsförsök faktiskt skulle
// kunna slå igenom om queryn byggdes med strängkonkatenering. Kontrolltestet först bevisar att
// testet inte är tomt: korrekta uppgifter loggar in.
@DataJpaTest
@Import(AuthService.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthServiceInjectionTest {

    private static final String EMAIL = "attestant@seb.test";
    private static final String PASSWORD = "Hemligt123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void seedUser() {
        String hash = new BCryptPasswordEncoder().encode(PASSWORD);
        userRepository.save(new User(1L, "Test Attestant", EMAIL, hash, Role.ATTESTANT));
        userRepository.flush();
    }

    @Test
    void controlCase_validCredentialsAuthenticate() {
        assertTrue(authService.authenticate(EMAIL, PASSWORD).isPresent());
    }

    @Test
    void wrongPassword_isRejected() {
        assertTrue(authService.authenticate(EMAIL, "fel-lösenord").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "' OR 1=1 --",
            "' OR '1'='1",
            "\" OR \"\"=\"",
            "attestant@seb.test' --",
            "attestant@seb.test'; DROP TABLE users; --",
            "' UNION SELECT * FROM users --"
    })
    void injectionInEmail_isRejected(String payload) {
        assertTrue(authService.authenticate(payload, PASSWORD).isEmpty());
        assertTrue(authService.authenticate(payload, "' OR 1=1 --").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "' OR 1=1 --",
            "' OR '1'='1",
            "x' OR password_hash LIKE '%"
    })
    void injectionInPassword_isRejected(String payload) {
        assertTrue(authService.authenticate(EMAIL, payload).isEmpty());
    }

    @Test
    void injectionAttemptsDoNotAlterData() {
        authService.authenticate("attestant@seb.test'; DROP TABLE users; --", PASSWORD);

        // Tabellen finns kvar och användaren är orörd
        assertEquals(1, userRepository.count());
        assertTrue(userRepository.findByEmail(EMAIL).isPresent());
    }
}
