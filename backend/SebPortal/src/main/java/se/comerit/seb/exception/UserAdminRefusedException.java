package se.comerit.seb.exception;

// Användaradministrationen vägrar ändringen, till exempel att ändra en administratörs roll. Ger 403 med
// meddelandet (GlobalExceptionHandler).
public class UserAdminRefusedException extends RuntimeException {

    public UserAdminRefusedException(String message) {
        super(message);
    }
}
