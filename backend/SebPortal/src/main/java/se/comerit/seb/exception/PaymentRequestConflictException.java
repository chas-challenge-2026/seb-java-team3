package se.comerit.seb.exception;

public class PaymentRequestConflictException extends RuntimeException {
    public PaymentRequestConflictException() {
        super("Betalningsförsöket har redan använts med andra uppgifter. Starta en ny betalning.");
    }
}
