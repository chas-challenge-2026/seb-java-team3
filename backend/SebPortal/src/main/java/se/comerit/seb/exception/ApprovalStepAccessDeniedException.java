package se.comerit.seb.exception;

public class ApprovalStepAccessDeniedException extends RuntimeException {
    public ApprovalStepAccessDeniedException(String message) {
        super(message);
    }
}