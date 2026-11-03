package se.comerit.seb.infrastructure.audit;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.util.Map;

/**
 * JNA interface to the native audit signer (libauditsigner.so, see native/audit).
 *
 * There is deliberately no static INSTANCE: a failed static load would poison this class for the
 * rest of the JVM. Use {@link #load()} and handle the {@link UnsatisfiedLinkError}.
 */
public interface AuditSignerLib extends Library {

    /** Appends one signed entry. Returns 0 on success, -1 on failure (nothing is written). */
    int audit_append(String logPath, String secretKey, int userId,
                     String action, int entityId, String description);

    /**
     * Verifies the whole log. Returns -1 if intact, the 1-indexed line of the first broken entry,
     * or 0 for bad arguments / an unreadable file.
     */
    int audit_verify(String logPath, String secretKey);

    static AuditSignerLib load() {
        // The C side treats strings as raw bytes, so pin the encoding instead of relying on the JVM default
        return Native.load("auditsigner", AuditSignerLib.class,
                Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
    }
}
