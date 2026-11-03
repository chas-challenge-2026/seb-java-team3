package se.comerit.seb.exception;

// Steget eller betalningen går inte att besluta om just nu: någon annan hann godkänna eller avvisa
// (till exempel attestanten och en admin samtidigt), eller ett tidigare steg väntar fortfarande.
// Ger 409 med ett meddelande som kan visas för användaren (GlobalExceptionHandler). Ärver
// IllegalStateException, så kod och tester som fångar det fungerar som förut.
public class ApprovalNotPendingException extends IllegalStateException {
    public ApprovalNotPendingException(String message) {
        super(message);
    }
}
