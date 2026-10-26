package se.comerit.seb.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.RefreshCookies;
import se.comerit.seb.security.RoleAccessDeniedHandler;
import se.comerit.seb.service.AuthService;
import se.comerit.seb.service.RefreshTokenService;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// BUG-002: hela vägen genom /api/auth/login med den riktiga AuthService. Bara repositoryt är
// mockat, så det är BCrypt-jämförelsen i AuthService som avgör svaret. En kvarvarande MD5-hash
// ska ge 401 utan token; kontrolltestet visar att samma lösenord ger 200 med en BCrypt-hash.
// RefreshTokenService är mockad: refresh-token prövas för sig i AuthControllerRefreshTest.
@WebMvcTest(AuthController.class)
@Import({JwtSecurityConfig.class, JwtService.class, JwtUserContext.class, RoleAccessDeniedHandler.class,
        AuthService.class, RefreshCookies.class})
class AuthControllerMd5LoginTest {

    private static final String EMAIL = "lisa@malmobygg.se";
    private static final String PASSWORD = "password123";
    // MD5("password123"), samma värde som seeden hade före V7
    private static final String MD5_HASH = "482c811da5d5b4bc6d497ffa98491e38";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void givenRefreshTokens() {
        when(refreshTokenService.issueForLogin(anyLong())).thenReturn("refresh-token");
        when(refreshTokenService.sessionLifetime()).thenReturn(Duration.ofDays(7));
    }

    @Test
    void login_withBcryptHash_returns200WithToken() throws Exception {
        givenUserWithHash(new BCryptPasswordEncoder().encode(PASSWORD));

        mockMvc.perform(login(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_withLegacyMd5Hash_returns401WithoutToken() throws Exception {
        givenUserWithHash(MD5_HASH);

        mockMvc.perform(login(PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void login_sendingTheMd5HashAsPassword_returns401() throws Exception {
        givenUserWithHash(MD5_HASH);

        mockMvc.perform(login(MD5_HASH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    private void givenUserWithHash(String passwordHash) {
        User user = new User(1L, "Lisa Persson", EMAIL, passwordHash, Role.INITIATOR);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, password));
    }
}
