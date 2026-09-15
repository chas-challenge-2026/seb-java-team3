package se.comerit.seb.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Role lookups for business logic that needs to branch by role (e.g. an admin seeing a
 * different data set than an attestant) rather than gate an endpoint outright. Endpoint
 * access itself should be declared with @PreAuthorize, not by calling this class.
 */
@Component
public class CurrentUserRoles {

    public boolean hasRole(String role) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }

        String target = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(target::equals);
    }
}
