package se.comerit.seb.exception;

// Rollbytet går inte nu, eftersom det skulle lämna arbete utan ägare (en attestant med väntande atteststeg).
// Ger 409 (GlobalExceptionHandler).
public class RoleChangeConflictException extends RuntimeException {

    public RoleChangeConflictException(String message) {
        super(message);
    }
}
