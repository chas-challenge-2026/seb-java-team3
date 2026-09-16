package se.comerit.seb.domain;

public enum Role {
    INITIATOR,
    ATTESTANT,
    ADMIN;

    /**
     * Parses a role stored on an HttpSession, which may be the enum itself or its
     * serialized name. Returns null when the value is missing or not a valid role.
     */
    public static Role fromSessionValue(Object value) {
        if (value instanceof Role role) {
            return role;
        }

        if (value instanceof String role) {
            try {
                return Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        return null;
    }
}
