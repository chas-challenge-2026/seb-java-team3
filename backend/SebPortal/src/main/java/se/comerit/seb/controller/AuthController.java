package se.comerit.seb.controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import se.comerit.seb.domain.User;
import se.comerit.seb.dto.LoginResponse;
import se.comerit.seb.dto.UserResponse;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.RefreshCookies;
import se.comerit.seb.service.AuthService;
import se.comerit.seb.service.RefreshTokenService;
import se.comerit.seb.service.RefreshTokenService.RotatedSession;

@RestController
public class AuthController {

    // Kravet på den här headern är det extra CSRF-skyddet för /refresh och /logout (ADR 0012).
    // Cookien är SameSite=Strict, men en egen header är ett andra lås: en annan sajt kan inte sätta
    // den utan att webbläsaren först skickar en förfrågan i förväg (preflight), som vi inte godkänner.
    // Värdet spelar ingen roll, det är närvaron som räknas.
    static final String REQUESTED_WITH_HEADER = "X-Requested-With";

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtUserContext jwtUserContext;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookies refreshCookies;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          JwtUserContext jwtUserContext,
                          UserRepository userRepository,
                          RefreshTokenService refreshTokenService,
                          RefreshCookies refreshCookies) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.jwtUserContext = jwtUserContext;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> apiLogin(
            @RequestBody LoginRequest request,
            @CookieValue(name = RefreshCookies.NAME, required = false) String previousRefreshToken) {
        Optional<User> user =
                authService.authenticate(request.getEmail(), request.getPassword());

        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        // Den gamla sessionen i den här webbläsaren ska inte leva vidare bredvid den nya: annars
        // blir dess token kvar aktiv i databasen, och en kopia av den kan roteras i upp till 7 dagar
        // utan att någonsin upptäckas (den riktiga användaren skickar den aldrig igen). Görs först
        // efter lyckad autentisering, så att ett felaktigt lösenord inte loggar ut någon.
        // revoke() hanterar saknad och okänd token, så en första inloggning behöver inget undantag.
        refreshTokenService.revoke(previousRefreshToken);

        String token = jwtService.generateToken(toAuthenticatedUserContext(user.get()));
        String refreshToken = refreshTokenService.issueForLogin(user.get().getId());

        // Svarskroppen är oförändrad (access-token + användare); refresh-token går bara i cookien.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookies.issue(refreshToken, refreshTokenService.sessionLifetime()).toString())
                .body(LoginResponse.from(user.get(), token));
    }

    // Förnyelse (ADR 0012): refresh-token i cookien byts mot en ny, och en ny access-token utfärdas.
    // Anropet kräver ingen Authorization-header, eftersom access-token oftast är utgången just då.
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = REQUESTED_WITH_HEADER, required = false) String requestedWith) {

        if (!hasRequestedWithHeader(requestedWith)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Missing " + REQUESTED_WITH_HEADER + " header"));
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No session"));
        }

        Optional<RotatedSession> rotated = refreshTokenService.rotate(refreshToken);

        if (rotated.isEmpty()) {
            // Okänd, utgången, spärrad eller återanvänd token. Cookien tas bort så att webbläsaren
            // inte fortsätter skicka en död token.
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                    .body(Map.of("error", "Session expired"));
        }

        User user = rotated.get().user();
        String accessToken = jwtService.generateToken(toAuthenticatedUserContext(user));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookies.issue(rotated.get().refreshToken(), refreshTokenService.sessionLifetime()).toString())
                .body(LoginResponse.from(user, accessToken));
    }

    // Utloggning: spärrar sessionen på servern och tömmer cookien. Ger alltid 204, även utan cookie,
    // så att utloggning går att anropa flera gånger.
    @PostMapping("/api/auth/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = REQUESTED_WITH_HEADER, required = false) String requestedWith) {

        if (!hasRequestedWithHeader(requestedWith)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Missing " + REQUESTED_WITH_HEADER + " header"));
        }

        refreshTokenService.revoke(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> currentUser() {
        AuthenticatedUserContext authenticated = jwtUserContext.requireAuthenticated();
        User user = userRepository.findById(authenticated.userId())
                .orElseThrow(() -> new IllegalStateException("User in token not found: " + authenticated.userId()));

        return ResponseEntity.ok(UserResponse.from(user));
    }

    private static boolean hasRequestedWithHeader(String requestedWith) {
        return requestedWith != null && !requestedWith.isBlank();
    }

    private AuthenticatedUserContext toAuthenticatedUserContext(User user) {
        return new AuthenticatedUserContext(user.getId(), user.getTenantId(), user.getRole());
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
