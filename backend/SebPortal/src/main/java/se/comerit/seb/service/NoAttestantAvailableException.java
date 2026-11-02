package se.comerit.seb.service;

public class NoAttestantAvailableException extends RuntimeException {

    public NoAttestantAvailableException(Long tenantId) {
        super("För få attestanter för betalningen hos tenant " + tenantId);
    }
}
