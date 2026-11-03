package se.comerit.seb.infrastructure.iban;

import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * Service for IBAN/BIC validation.
 * Uses native C library via JNA when available, falls back to a pure-Java MOD97 check if not.
 */
@Service
public class IbanValidatorService {
    private static final Logger logger = LoggerFactory.getLogger(IbanValidatorService.class);

    // Known-valid IBAN used to smoke-test the native library at startup.
    private static final String KNOWN_VALID_IBAN = "SE4550000000058398257466";

    // Error codes (must match C implementation)
    private static final int ERROR_INVALID_LENGTH = 1;
    private static final int ERROR_INVALID_COUNTRY = 2;
    private static final int ERROR_INVALID_CHAR = 3;
    private static final int ERROR_MOD97_FAILED = 4;

    private final boolean nativeLibAvailable;

    public IbanValidatorService(@Value("${iban.native-required:false}") boolean nativeRequired) {
        this.nativeLibAvailable = checkNativeLibrary();
        if (nativeRequired && !nativeLibAvailable) {
            throw new IllegalStateException(
                    "iban.native-required=true but the native IBAN validator library is not available");
        }
    }

    /**
     * Check if native library is available at startup by validating a known-valid IBAN,
     * not just checking that the call doesn't throw.
     */
    private static boolean checkNativeLibrary() {
        try {
            int result = IbanLib.INSTANCE.validate_iban(KNOWN_VALID_IBAN, new IntByReference());
            if (result != 1) {
                logger.warn("⚠ Native IBAN validator library loaded but failed to validate a known-valid IBAN, " +
                           "using Java MOD97 fallback.");
                return false;
            }
            logger.info("✓ Native IBAN validator library loaded successfully");
            return true;
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError e) {
            logger.warn("⚠ Native IBAN validator library not found, using Java MOD97 fallback. " +
                       "Set -Djna.library.path=native/build/lib at startup to enable.");
            return false;
        }
    }

    /**
     * Remove spaces, hyphens and NBSP, uppercase, and reject IBANs that are too long or
     * contain a NUL character (which would silently truncate validation on the C side).
     *
     * @param iban Raw user input
     * @return Normalized IBAN, safe to store and pass to native/regex validation
     */
    public String normalize(String iban) {
        if (iban == null) {
            return null;
        }
        if (iban.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("IBAN contains invalid characters");
        }
        String normalized = iban.replaceAll("[\\s\\u00A0-]", "").toUpperCase();
        if (normalized.length() > 34) {
            throw new IllegalArgumentException("IBAN must not exceed 34 characters");
        }
        return normalized;
    }

    /**
     * Validate IBAN using native library or Java MOD97 fallback.
     * 
     * @param iban The IBAN to validate
     * @return true if valid, false otherwise
     */
    public boolean validateIban(String iban) {
        if (iban == null || iban.isBlank()) {
            return false;
        }

        if (nativeLibAvailable) {
            return validateIbanNative(iban);
        } else {
            return validateIbanMod97(iban);
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
                // GDPR: never log the full IBAN, only a masked variant plus the error code.
                logger.debug("IBAN validation failed for {} (error code: {})", maskIban(iban), errorOut.getValue());
            }
            
            return result == 1;
        } catch (Exception e) {
            logger.warn("Error calling native IBAN validator, falling back to Java MOD97", e);
            return validateIbanMod97(iban);
        }
    }

    /**
     * Mask an IBAN for logging: keep the country code and check digits, and the last
     * 4 characters (e.g. "SE45…7466"), hiding the rest of the account number.
     */
    private static String maskIban(String iban) {
        String stripped = iban.replaceAll("[\\s\\u00A0-]", "");
        if (stripped.length() <= 8) {
            return "***";
        }
        return stripped.substring(0, 4) + "\u2026" + stripped.substring(stripped.length() - 4);
    }

    /**
     * Validate IBAN in pure Java (fallback when the native library is unavailable).
     * Checks format and the ISO 13616 MOD97 checksum, so invalid IBANs aren't let through.
     */
    private boolean validateIbanMod97(String iban) {
        String normalized = iban.replaceAll("[\\s\\u00A0-]", "").toUpperCase();

        // Basic format: 2 letters + 2 digits + 11-30 alphanumeric
        if (!normalized.matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$")) {
            return false;
        }

        return mod97(normalized) == 1;
    }

    /**
     * ISO 13616 MOD97 checksum: move the first 4 characters to the end, map letters to
     * A=10..Z=35, and check that the resulting number mod 97 equals 1.
     */
    private static int mod97(String normalizedIban) {
        String rearranged = normalizedIban.substring(4) + normalizedIban.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else {
                numeric.append(Character.getNumericValue(c));
            }
        }
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue();
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

        if (nativeLibAvailable) {
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
        if (!nativeLibAvailable) {
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
