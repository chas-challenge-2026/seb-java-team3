package se.comerit.seb.dto;

import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;

public record LoginResponse(
        String token,
        Long id,
        String name,
        String email,
        Role role
) {
    public static LoginResponse from(User user, String token) {
        return new LoginResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
