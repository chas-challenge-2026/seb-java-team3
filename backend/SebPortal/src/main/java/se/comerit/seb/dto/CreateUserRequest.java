package se.comerit.seb.dto;

// Det en admin skickar för att skapa en användare. Företaget finns medvetet inte med: det kommer alltid
// från admins token, aldrig från anropet. Rollen är en sträng så att en okänd roll ger ett begripligt 400
// i vårt vanliga felformat i stället för ett tolkningsfel.
public record CreateUserRequest(
        String name,
        String email,
        String password,
        String role
) {
}
