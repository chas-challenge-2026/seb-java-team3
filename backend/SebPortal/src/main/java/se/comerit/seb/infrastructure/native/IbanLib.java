package se.comerit.seb.infrastructure.native;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

/**
 * JNA interface to the native IBAN validator library (libiban.so)
 * 
 * Maps C functions to Java methods. Load via:
 *   IbanLib.INSTANCE.validate_iban(...)
 */
public interface IbanLib extends Library {
    // Load the native library (JNA searches jna.library.path, java.library.path, etc.)
    IbanLib INSTANCE = Native.load("iban", IbanLib.class);

    /**
     * Validate IBAN according to ISO 13616 (MOD97 checksum).
     * 
     * @param iban The IBAN string to validate
     * @param errorOut Pointer to error code (only set if validation fails)
     * @return 1 if valid, 0 if invalid
     */
    int validate_iban(String iban, IntByReference errorOut);

    /**
     * Validate BIC according to ISO 9362.
     * 
     * @param bic The BIC string to validate
     * @return 1 if valid, 0 if invalid
     */
    int validate_bic(String bic);
}