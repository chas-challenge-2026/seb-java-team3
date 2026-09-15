package se.comerit.seb.infrastructure.native;

import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for IBAN/BIC validation.
 * Uses native C library via JNA when available, falls back to regex if not.
 */
@Service
public class IbanValidatorService {
    private static final Logger logger = LoggerFactory.getLogger(IbanValidatorService.class);
    private static final boolean NATIVE_LIB_AVAILABLE = checkNativeLibrary();

    // Error codes (must match C implementation)
    private static final int ERROR_INVALID_LENGTH = 1;
    private static final int ERROR_INVALID_COUNTRY = 2;
    private static final int ERROR_INVALID_CHAR = 3;
    private static final int ERROR_MOD97_FAILED = 4;

    /**
     * Check if native library is available at startup.
     */
    private static boolean checkNativeLibrary() {
        try {
            IbanLib.INSTANCE.validate_iban("SE4550000000058398257466", new IntByReference());
            logger.info("✓ Native IBAN validator library loaded successfully");
            return true;
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
            logger.warn("⚠ Native IBAN validator library not found, using regex fallback. " +
                       "Set -Djna.library.path=native/ at startup to enable.");
            return false;
        }
    }

    /**
     * Validate IBAN using native library or regex fallback.
     * 
     * @param iban The IBAN to validate
     * @return true if valid, false otherwise
     */
    public boolean validateIban(String iban) {
        if (iban == null || iban.isBlank()) {
            return false;
        }

        if (NATIVE_LIB_AVAILABLE) {
            return validateIbanNative(iban);
        } else {
            return validateIbanRegex(iban);
        }
    }

    /**
     * Validate IBAN using native C library.
     */
    private boolean validateIbanNative(String iban) {
        try {
            IntByReference errorOut = new IntByReference();
            int result = IbanLib.INSTANCE.validate_iban(iban, errorOut);
            
            if (result == 0) {
                logger.debug("IBAN validation failed: {} (error code: {})", iban, errorOut.getValue());
            }
            
            return result == 1;
        } catch (Exception e) {
            logger.warn("Error calling native IBAN validator, falling back to regex", e);
            return validateIbanRegex(iban);
        }
    }

    /**
     * Validate IBAN using regex (v1 fallback).
     * Simple format check only (doesn't verify MOD97 checksum).
     */
    private boolean validateIbanRegex(String iban) {
        // Remove spaces and hyphens
        String normalized = iban.replaceAll("[\\s-]", "").toUpperCase();
        
        // Basic format: 2 letters + 2 digits + 11-30 alphanumeric
        return normalized.matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");
    }

    /**
     * Validate BIC using native library or simple format check.
     * 
     * @param bic The BIC to validate
     * @return true if valid, false otherwise
     */
    public boolean validateBic(String bic) {
        if (bic == null || bic.isBlank()) {
            return false;
        }

        if (NATIVE_LIB_AVAILABLE) {
            try {
                int result = IbanLib.INSTANCE.validate_bic(bic);
                return result == 1;
            } catch (Exception e) {
                logger.warn("Error calling native BIC validator", e);
                return validateBicRegex(bic);
            }
        } else {
            return validateBicRegex(bic);
        }
    }

    /**
     * Validate BIC using regex (format only).
     * Format: AAAA BB CC [DDD]
     */
    private boolean validateBicRegex(String bic) {
        String normalized = bic.toUpperCase();
        // 8 or 11 characters, letters/digits only
        if (!normalized.matches("^[A-Z0-9]{8}([A-Z0-9]{3})?$")) {
            return false;
        }
        // First 4 must be letters, chars 5-6 must be letters
        return normalized.matches("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    }

    /**
     * Get human-readable error message for IBAN validation failure.
     */
    public String getIbanErrorMessage(String iban) {
        if (!NATIVE_LIB_AVAILABLE) {
            return "IBAN format is invalid";
        }

        IntByReference errorOut = new IntByReference();
        IbanLib.INSTANCE.validate_iban(iban, errorOut);

        return switch (errorOut.getValue()) {
            case ERROR_INVALID_LENGTH -> "IBAN must be 15-34 characters";
            case ERROR_INVALID_COUNTRY -> "Invalid country code (first 2 characters must be letters)";
            case ERROR_INVALID_CHAR -> "IBAN contains invalid characters";
            case ERROR_MOD97_FAILED -> "Invalid IBAN checksum";
            default -> "IBAN is invalid";
        };
    }
}