package se.comerit.seb.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import se.comerit.seb.service.NoAttestantAvailableException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Fasta texter för de fel vars undantag innehåller interna id:n (R-18). Detaljerna går till serverloggen.
    static final String NO_ATTESTANT_MESSAGE =
            "Det finns inte tillräckligt många attestanter för att ta emot betalningen. Kontakta er administratör.";
    static final String ACCESS_DENIED_MESSAGE = "Du har inte behörighet att hantera det här atteststeget.";
    static final String UNEXPECTED_ERROR_MESSAGE = "Något gick fel på servern. Försök igen om en stund.";

    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String message
    ) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NoAttestantAvailableException.class)
    public ResponseEntity<ErrorResponse> handleNoAttestant(NoAttestantAvailableException ex) {
        log.warn(ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                NO_ATTESTANT_MESSAGE
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(UserAdminRefusedException.class)
    public ResponseEntity<ErrorResponse> handleUserAdminRefused(UserAdminRefusedException ex) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(UserDeleteConflictException.class)
    public ResponseEntity<ErrorResponse> handleUserDeleteConflict(UserDeleteConflictException ex) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RoleChangeConflictException.class)
    public ResponseEntity<ErrorResponse> handleRoleChangeConflict(RoleChangeConflictException ex) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Betalningen hann behandlas av någon annan (eller ett tidigare steg väntar): 409 och ett begripligt
    // meddelande i stället för 500. Andra IllegalStateException är riktiga internfel och hanteras inte här.
    @ExceptionHandler(ApprovalNotPendingException.class)
    public ResponseEntity<ErrorResponse> handleApprovalNotPending(ApprovalNotPendingException ex) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(se.comerit.seb.exception.ApprovalStepAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleApprovalStepAccessDenied(
            se.comerit.seb.exception.ApprovalStepAccessDeniedException ex) {
        log.warn(ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ACCESS_DENIED_MESSAGE
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // Allt annat är ett internfel: klienten får en fast text, och felet med stack trace går till serverloggen.
    // Fel som Spring ska hantera kastas vidare, så att till exempel @PreAuthorize fortfarande ger 403 och
    // en trasig request 400 i stället för 500.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) throws Exception {
        if (isHandledBySpring(ex)) {
            throw ex;
        }

        log.error("Oväntat fel i ett API-anrop", ex);
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                UNEXPECTED_ERROR_MESSAGE
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static boolean isHandledBySpring(Exception ex) {
        return ex instanceof org.springframework.web.ErrorResponse
                || ex instanceof AccessDeniedException
                || ex instanceof AuthenticationException
                || ex instanceof HttpMessageConversionException
                || ex instanceof TypeMismatchException
                || ex instanceof BindException;
    }
}
