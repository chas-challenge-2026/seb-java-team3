package se.comerit.seb.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import se.comerit.seb.domain.Role;

@Component
public class SessionUserContext {

    public AuthenticatedUserContext requireAuthenticated(HttpSession session) {
        Long userId = sessionLong(session, "userId");
        Long tenantId = sessionLong(session, "tenantId");
        Role role = sessionRole(session);

        if (userId == null || tenantId == null || role == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }

        return new AuthenticatedUserContext(userId, tenantId, role);
    }

    private Long sessionLong(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Role sessionRole(HttpSession session) {
        return Role.fromSessionValue(session.getAttribute("role"));
    }
}
