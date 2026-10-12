package se.comerit.seb.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Bygger refresh-token-cookien (ADR 0012). Alla attribut ligger på ett ställe så att inloggning,
// förnyelse och utloggning garanterat sätter och tömmer exakt samma cookie.
//
//  - HttpOnly:        JavaScript kan inte läsa den, så en XSS-attack kan inte stjäla den
//  - SameSite=Strict: webbläsaren skickar den aldrig från en annan sajt (CSRF-skydd)
//  - Path=/api/auth:  den skickas bara till inloggnings-, förnyelse- och utloggningsendpointsen,
//                     aldrig till övriga API:t
//  - Secure:          bara över HTTPS (stage och prod); webbläsare tillåter den även på localhost
@Component
public class RefreshCookies {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/auth";

    private final boolean secure;

    public RefreshCookies(@Value("${app.refresh.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie issue(String refreshToken, Duration maxAge) {
        return builder(refreshToken).maxAge(maxAge).build();
    }

    // Max-Age=0 med samma namn och sökväg gör att webbläsaren tar bort cookien.
    public ResponseCookie clear() {
        return builder("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
