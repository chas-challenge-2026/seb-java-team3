package se.comerit.seb.exception;

// Användaren finns inte i admins företag. Ett annat företags användare ser ut precis så, så att id:n hos andra
// kunder inte går att kartlägga. Ger 404 (GlobalExceptionHandler).
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("Användaren finns inte.");
    }
}
