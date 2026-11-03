package se.comerit.seb.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JwtUserContext {

    public AuthenticatedUserContext requireAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUserContext user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }

        return user;
    }

    public AuthenticatedUserContext requireAdmin() {
        AuthenticatedUserContext user = requireAuthenticated();

        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }

        return user;
    }

    public AuthenticatedUserContext requireAdminOrAttestant() {
        AuthenticatedUserContext user = requireAuthenticated();

        if (!user.isAdmin() && !user.isAttestant()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or attestant role required");
        }

        return user;
    }
}
