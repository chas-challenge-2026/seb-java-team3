package se.comerit.seb.exception;

// E-postadressen är redan registrerad hos en annan användare. Ger 409 (GlobalExceptionHandler).
public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException() {
        super("E-postadressen används redan.");
    }
}
