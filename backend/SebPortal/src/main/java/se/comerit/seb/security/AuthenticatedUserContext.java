package se.comerit.seb.security;

import se.comerit.seb.domain.Role;

public record AuthenticatedUserContext(
        Long userId,
        Long tenantId,
        Role role
) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isAttestant() {
        return role == Role.ATTESTANT;
    }
}
