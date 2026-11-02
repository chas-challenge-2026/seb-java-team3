package se.comerit.seb.exception;

// Användaren går inte att ta bort, eftersom betalningar eller atteststeg pekar på hen och historiken måste
// bevaras. Ger 409 (GlobalExceptionHandler).
public class UserDeleteConflictException extends RuntimeException {

    public UserDeleteConflictException(String message) {
        super(message);
    }
}
