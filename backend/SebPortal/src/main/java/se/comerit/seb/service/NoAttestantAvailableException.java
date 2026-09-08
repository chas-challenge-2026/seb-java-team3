package se.comerit.seb.service;

public class NoAttestantAvailableException extends RuntimeException {

    public NoAttestantAvailableException(Long tenantId) {
        super("Ingen attestant hittades för tenant " + tenantId);
    }
}