package se.comerit.seb.dto;

// Den nya rollen för en användare. Rollen är en sträng så att en okänd roll ger ett begripligt 400 i vårt
// vanliga felformat (samma som CreateUserRequest).
public record ChangeUserRoleRequest(String role) {
}
