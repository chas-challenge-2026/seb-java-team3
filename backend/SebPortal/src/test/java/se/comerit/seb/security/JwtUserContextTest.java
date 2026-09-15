package se.comerit.seb.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import se.comerit.seb.domain.Role;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUserContextTest {

    private final JwtUserContext jwtUserContext = new JwtUserContext();

    @AfterEach
    void clearSecurityContext() {
        // SecurityContextHolder ligger i en ThreadLocal - måste nollställas mellan tester,
        // annars läcker autentiseringen från ett test in i nästa.
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireAuthenticated_shouldReturnUserWhenAuthenticationPresent() {
        AuthenticatedUserContext expected = new AuthenticatedUserContext(1L, 2L, Role.ATTESTANT);
        authenticateAs(expected);

        assertEquals(expected, jwtUserContext.requireAuthenticated());
    }

    @Test
    void requireAuthenticated_shouldRejectWhenNoAuthenticationSet() {
        assertThrows(ResponseStatusException.class, jwtUserContext::requireAuthenticated);
    }

    @Test
    void requireAdmin_shouldRejectNonAdmin() {
        authenticateAs(new AuthenticatedUserContext(1L, 1L, Role.INITIATOR));

        assertThrows(ResponseStatusException.class, jwtUserContext::requireAdmin);
    }

    @Test
    void requireAdmin_shouldAllowAdmin() {
        AuthenticatedUserContext admin = new AuthenticatedUserContext(1L, 1L, Role.ADMIN);
        authenticateAs(admin);

        assertEquals(admin, jwtUserContext.requireAdmin());
    }

    @Test
    void requireAdminOrAttestant_shouldAllowAttestantButRejectInitiator() {
        authenticateAs(new AuthenticatedUserContext(1L, 1L, Role.ATTESTANT));
        assertEquals(Role.ATTESTANT, jwtUserContext.requireAdminOrAttestant().role());

        authenticateAs(new AuthenticatedUserContext(1L, 1L, Role.INITIATOR));
        assertThrows(ResponseStatusException.class, jwtUserContext::requireAdminOrAttestant);
    }

    private void authenticateAs(AuthenticatedUserContext user) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(authority)));
    }
}
