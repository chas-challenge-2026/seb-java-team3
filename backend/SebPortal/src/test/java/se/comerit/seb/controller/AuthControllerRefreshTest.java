package se.comerit.seb.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.RefreshCookies;
import se.comerit.seb.security.RoleAccessDeniedHandler;
import se.comerit.seb.service.AuthService;
import se.comerit.seb.service.RefreshTokenService;
import se.comerit.seb.service.RefreshTokenService.RotatedSession;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ADR 0012: login, förnyelse och utloggning med refresh-cookien, genom hela säkerhetskedjan (JWT-filter,
// JwtSecurityConfig, controller). Tjänsten som hanterar token är mockad; den prövas för sig i
// RefreshTokenServiceTest. Här är det HTTP-lagret som bevisas: cookie-attribut, statuskoder och
// att /refresh och /logout går att anropa utan en (utgången) access-token.
@WebMvcTest(AuthController.class)
@Import({JwtSecurityConfig.class, RoleAccessDeniedHandler.class, JwtService.class, JwtUserContext.class,
        RefreshCookies.class})
class AuthControllerRefreshTest {

    private static final String OLD_TOKEN = "old-refresh-token-value";
    private static final String NEW_TOKEN = "new-refresh-token-value";
    private static final String HEADER = AuthController.REQUESTED_WITH_HEADER;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RefreshTokenService refreshTokenService;

    private User lisa;

    @BeforeEach
    void setUp() {
        lisa = new User(1L, "Lisa Persson", "lisa@malmobygg.se", "hash", Role.INITIATOR);
        ReflectionTestUtils.setField(lisa, "id", 7L);
        when(refreshTokenService.sessionLifetime()).thenReturn(Duration.ofDays(7));
    }

    // ---- login ----

    @Test
    void login_setsAHardenedRefreshCookie_andKeepsTheResponseBody() throws Exception {
        when(authService.authenticate("lisa@malmobygg.se", "password123")).thenReturn(Optional.of(lisa));
        when(refreshTokenService.issueForLogin(7L)).thenReturn(OLD_TOKEN);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"lisa@malmobygg.se\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("lisa@malmobygg.se"))
                .andExpect(jsonPath("$.role").value("INITIATOR"))
                .andReturn();

        assertRefreshCookie(result.getResponse().getHeader(HttpHeaders.SET_COOKIE), OLD_TOKEN, "Max-Age=604800");
        // Refresh-token finns bara i cookien, aldrig i svarskroppen där JavaScript kan läsa den
        assertFalse(result.getResponse().getContentAsString().contains(OLD_TOKEN));
    }

    @Test
    void login_withWrongCredentials_returns401AndSetsNoCookie() throws Exception {
        when(authService.authenticate("lisa@malmobygg.se", "fel")).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"lisa@malmobygg.se\",\"password\":\"fel\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertNull(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        verify(refreshTokenService, never()).issueForLogin(any());
    }

    // ---- refresh ----

    @Test
    void refresh_withValidCookie_returnsANewAccessTokenAndRotatesTheCookie() throws Exception {
        when(refreshTokenService.rotate(OLD_TOKEN)).thenReturn(Optional.of(new RotatedSession(NEW_TOKEN, lisa)));

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshCookies.NAME, OLD_TOKEN))
                        .header(HEADER, "fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.role").value("INITIATOR"))
                .andReturn();

        // Cookien byts mot den nya token, med samma säkerhetsattribut som vid inloggning
        assertRefreshCookie(result.getResponse().getHeader(HttpHeaders.SET_COOKIE), NEW_TOKEN, "Max-Age=604800");

        // Svaret innehåller en riktig, giltig access-token för användaren
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        assertEquals(new AuthenticatedUserContext(7L, 1L, Role.INITIATOR), jwtService.parseToken(accessToken));
    }

    @Test
    void refresh_worksWithoutAnAuthorizationHeader_andIgnoresAnExpiredOne() throws Exception {
        when(refreshTokenService.rotate(OLD_TOKEN)).thenReturn(Optional.of(new RotatedSession(NEW_TOKEN, lisa)));

        // Den vanliga situationen: access-token har gått ut, så den som skickas är ogiltig
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer an-expired-or-garbage-token")
                        .cookie(new Cookie(RefreshCookies.NAME, OLD_TOKEN))
                        .header(HEADER, "fetch"))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_withoutTheRequestedWithHeader_returns403AndNeverTouchesTheToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshCookies.NAME, OLD_TOKEN)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refresh_withoutACookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header(HEADER, "fetch"))
                .andExpect(status().isUnauthorized());

        verify(refreshTokenService, never()).rotate(any());
    }

    @Test
    void refresh_withAnInvalidToken_returns401AndClearsTheCookie() throws Exception {
        when(refreshTokenService.rotate(OLD_TOKEN)).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshCookies.NAME, OLD_TOKEN))
                        .header(HEADER, "fetch"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Cookien tas bort (Max-Age=0), så webbläsaren slutar skicka en död token
        assertRefreshCookie(result.getResponse().getHeader(HttpHeaders.SET_COOKIE), "", "Max-Age=0");
    }

    // ---- logout ----

    @Test
    void logout_revokesTheSession_andClearsTheCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(RefreshCookies.NAME, OLD_TOKEN))
                        .header(HEADER, "fetch"))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(refreshTokenService).revoke(OLD_TOKEN);
        assertRefreshCookie(result.getResponse().getHeader(HttpHeaders.SET_COOKIE), "", "Max-Age=0");
    }

    @Test
    void logout_withoutACookie_stillReturns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header(HEADER, "fetch"))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_withoutTheRequestedWithHeader_returns403AndRevokesNothing() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(RefreshCookies.NAME, OLD_TOKEN)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    // ---- övriga endpoints påverkas inte ----

    @Test
    void otherApiEndpoints_stillRequireAnAccessToken_evenWithARefreshCookie() throws Exception {
        // Refresh-cookien är ingen inloggning: /api/auth/me kräver fortfarande en access-token
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie(RefreshCookies.NAME, OLD_TOKEN)))
                .andExpect(status().isUnauthorized());
    }

    // ---- hjälpmetoder ----

    private static void assertRefreshCookie(String setCookie, String expectedValue, String expectedMaxAge) {
        assertNotNull(setCookie, "Set-Cookie saknas");
        assertTrue(setCookie.startsWith(RefreshCookies.NAME + "=" + expectedValue + ";"),
                "oväntat cookie-värde: " + setCookie);
        assertTrue(setCookie.contains("HttpOnly"), "HttpOnly saknas: " + setCookie);
        assertTrue(setCookie.contains("Secure"), "Secure saknas: " + setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), "SameSite=Strict saknas: " + setCookie);
        assertTrue(setCookie.contains("Path=/api/auth"), "Path=/api/auth saknas: " + setCookie);
        assertTrue(setCookie.contains(expectedMaxAge), expectedMaxAge + " saknas: " + setCookie);
    }
}
