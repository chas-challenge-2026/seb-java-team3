package se.comerit.seb.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Ingen @Component har avsiktligt - se förklaringen i steg 4.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());

            try {
                AuthenticatedUserContext user = jwtService.parseToken(token);
                SecurityContextHolder.getContext().setAuthentication(toAuthentication(user));
            } catch (JwtException | IllegalArgumentException ignored) {
                // Ogiltig/utgången/manipulerad token, eller en claim som inte gick att tolka
                // (t.ex. ett role-namn som inte finns i Role-enumet) => ingen inloggning sätts.
                // SecurityConfig avgör sen om den anropade endpointen kräver auth (401 följer normalt).
            }
        }

        filterChain.doFilter(request, response);
    }

    private Authentication toAuthentication(AuthenticatedUserContext user) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
        return new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
    }
}
