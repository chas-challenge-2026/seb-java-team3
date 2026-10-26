package se.comerit.seb.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes the tamper-evident, HMAC-chained audit log through the native signer and verifies it.
 *
 * The log complements the audit_entries table, it does not replace it. A signing failure never
 * breaks the business operation: it is logged, counted and turns the health check red.
 */
@Service
public class AuditSignerService {

    private static final Logger logger = LoggerFactory.getLogger(AuditSignerService.class);

    /** Must match the default in application.properties. It must never be used in production. */
    static final String DEV_SIGNING_KEY = "dev-only-audit-signing-key-not-for-production";

    // Limits of the native API (see native/audit/audit_signer.h); they count bytes, not characters
    static final int MAX_ACTION_BYTES = 64;
    static final int MAX_DESCRIPTION_BYTES = 1024;

    private static final Duration VERIFY_CACHE_TTL = Duration.ofSeconds(60);

    public enum State { DISABLED, OK, BROKEN, ERROR }

    /** brokenLine is the 1-indexed line of the first broken entry, only set when state is BROKEN. */
    public record Integrity(State state, int brokenLine) { }

    private final AuditSignerLib lib;
    private final String signingKey;
    private final String logPath;
    private final AtomicLong failedAppends = new AtomicLong();

    private Integrity cachedIntegrity;
    private long cachedAtNanos;

    @Autowired
    public AuditSignerService(@Value("${audit.signing-key:}") String signingKey,
                              @Value("${audit.log-path:}") String logPath,
                              @Value("${audit.signing-required:false}") boolean required) {
        this(loadNativeLibrary(), signingKey, logPath, required);
    }

    /** Package-private so tests can supply a fake library, or null for "not available". */
    AuditSignerService(AuditSignerLib lib, String signingKey, String logPath, boolean required) {
        if (required && DEV_SIGNING_KEY.equals(signingKey)) {
            throw new IllegalStateException(
                    "audit.signing-required=true but audit.signing-key is the dev default. Set AUDIT_SIGNING_KEY.");
        }
        boolean configured = signingKey != null && !signingKey.isBlank()
                && logPath != null && !logPath.isBlank();
        if (required && !configured) {
            throw new IllegalStateException(
                    "audit.signing-required=true but audit.signing-key or audit.log-path is not set");
        }
        if (required && lib == null) {
            throw new IllegalStateException(
                    "audit.signing-required=true but the native audit signer library is not available");
        }

        if (lib != null && configured && prepareLogDirectory(logPath)) {
            this.lib = lib;
            this.signingKey = signingKey;
            this.logPath = logPath;
            logger.info("✓ Audit signing enabled, log file: {}", logPath);
        } else {
            this.lib = null;
            this.signingKey = null;
            this.logPath = null;
            logger.warn("⚠ Audit signing is disabled (native library, key or log path missing). " +
                    "Set -Djna.library.path=native/build/lib at startup to enable.");
        }
    }

    private static AuditSignerLib loadNativeLibrary() {
        try {
            return AuditSignerLib.load();
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError e) {
            return null;
        }
    }

    private static boolean prepareLogDirectory(String logPath) {
        try {
            Path parent = Path.of(logPath).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            logger.error("Cannot create the directory for the audit log {}: {}", logPath, e.toString());
            return false;
        }
    }

    public boolean isEnabled() {
        return lib != null;
    }

    public long getFailedAppends() {
        return failedAppends.get();
    }

    /**
     * Appends a signed entry. Never throws. Returns false if signing is disabled or the write failed
     * (failures are counted and logged).
     */
    public boolean append(Long userId, String action, Long entityId, String description) {
        if (lib == null) {
            return false;
        }
        try {
            int rc = lib.audit_append(
                    logPath,
                    signingKey,
                    toInt(userId),
                    fitUtf8(clean(action, "UNKNOWN"), MAX_ACTION_BYTES),
                    toInt(entityId),
                    fitUtf8(clean(description, ""), MAX_DESCRIPTION_BYTES));
            if (rc != 0) {
                recordFailure("audit_append returned " + rc);
                return false;
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            recordFailure(e.toString());
            return false;
        }
    }

    /** Verifies the log now and logs the outcome. */
    public synchronized Integrity verify() {
        Integrity result = doVerify();
        cachedIntegrity = result;
        cachedAtNanos = System.nanoTime();

        switch (result.state()) {
            case OK -> logger.info("✓ Audit log chain verified");
            case BROKEN -> logger.error("✗ Audit log is TAMPERED or corrupt, first broken entry is line {}",
                    result.brokenLine());
            case ERROR -> logger.error("✗ Audit log could not be verified (unreadable file or bad arguments)");
            case DISABLED -> { }
        }
        return result;
    }

    /** Like {@link #verify()} but reuses a result younger than a minute, so health checks stay cheap. */
    public synchronized Integrity integrity() {
        boolean fresh = cachedIntegrity != null
                && System.nanoTime() - cachedAtNanos < VERIFY_CACHE_TTL.toNanos();
        return fresh ? cachedIntegrity : verify();
    }

    @EventListener(ApplicationReadyEvent.class)
    void verifyAtStartup() {
        verify();
    }

    private Integrity doVerify() {
        if (lib == null) {
            return new Integrity(State.DISABLED, 0);
        }
        try {
            int rc = lib.audit_verify(logPath, signingKey);
            if (rc == -1) {
                return new Integrity(State.OK, 0);
            }
            if (rc >= 1) {
                return new Integrity(State.BROKEN, rc);
            }
            return new Integrity(State.ERROR, 0);
        } catch (RuntimeException | LinkageError e) {
            logger.error("Audit log verification failed: {}", e.toString());
            return new Integrity(State.ERROR, 0);
        }
    }

    private void recordFailure(String reason) {
        long total = failedAppends.incrementAndGet();
        logger.error("Could not write the signed audit entry ({}). Failures since startup: {}", reason, total);
    }

    private static int toInt(Long value) {
        if (value == null) {
            return 0;
        }
        return Math.toIntExact(value);
    }

    /** The native API rejects CR/LF, and a NUL would silently cut the string, so replace them. */
    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("[\\r\\n\\u0000]+", " ");
    }

    /** Cuts to at most maxBytes of UTF-8 without splitting a character. */
    static String fitUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) {
                break;
            }
            out.appendCodePoint(cp);
            bytes += size;
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}
