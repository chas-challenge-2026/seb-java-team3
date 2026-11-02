package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.config.PasswordEncoderConfig;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Inloggningen normaliserar e-postadressen (EmailAddress) innan den slås upp, eftersom alla adresser
// sparas med små bokstäver och utan omgivande mellanslag. Kör mot H2 i minnet.
@DataJpaTest
@Import({AuthService.class, PasswordEncoderConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthServiceEmailTest {

    private static final String PASSWORD = "Hemligt123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void seedUser() {
        userRepository.saveAndFlush(
                new User(1L, "Lisa Persson", "lisa@seb.test", passwordEncoder.encode(PASSWORD), Role.INITIATOR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lisa@seb.test", "LISA@SEB.TEST", "Lisa@Seb.Test", "  lisa@seb.test  ", "\tLisa@seb.test\n"})
    void anyCasingAndSurroundingWhitespace_findsTheSameUser(String typed) {
        assertEquals("Lisa Persson", authService.authenticate(typed, PASSWORD).orElseThrow().getName());
    }

    @Test
    void aWrongPassword_isStillRejected_whateverTheCasing() {
        assertTrue(authService.authenticate("LISA@seb.test", "fel-lösenord").isEmpty());
    }

    // Ett tomt namn får aldrig slås upp som "email IS NULL": en användare utan e-post med rätt lösenord
    // ska inte gå att logga in som.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void aBlankEmail_neverFindsAUserWithoutAnEmail(String typed) {
        userRepository.saveAndFlush(
                new User(1L, "Utan e-post", null, passwordEncoder.encode("annat-lösenord"), Role.INITIATOR));

        assertTrue(authService.authenticate(typed, "annat-lösenord").isEmpty());
    }
}
