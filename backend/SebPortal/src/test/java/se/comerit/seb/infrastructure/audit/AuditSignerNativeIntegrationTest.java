package se.comerit.seb.infrastructure.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real native library. Skipped when libauditsigner.so has not been built
 * (cd native && make lib), so it does not break a plain `mvn test`.
 */
class AuditSignerNativeIntegrationTest {

    private static final String KEY = "integration-test-key";
    private static final Path NATIVE_LIB_DIR = Path.of("..", "..", "native", "build", "lib").toAbsolutePath().normalize();

    @TempDir
    Path tempDir;

    private String previousJnaPath;
    private Path logFile;

    @BeforeEach
    void pointJnaAtBuiltLibrary() {
        assumeTrue(Files.exists(NATIVE_LIB_DIR.resolve("libauditsigner.so")),
                "libauditsigner.so not built, run `make lib` in native/");
        previousJnaPath = System.getProperty("jna.library.path");
        System.setProperty("jna.library.path", NATIVE_LIB_DIR.toString());
        logFile = tempDir.resolve("audit").resolve("audit.log");
    }

    @AfterEach
    void restoreJnaPath() {
        if (previousJnaPath == null) {
            System.clearProperty("jna.library.path");
        } else {
            System.setProperty("jna.library.path", previousJnaPath);
        }
    }

    private AuditSignerService service() {
        return new AuditSignerService(KEY, logFile.toString(), true);
    }

    @Test
    void createsMissingDirectoryAndWritesAVerifiableChain() throws IOException {
        AuditSignerService service = service();
        assertTrue(service.isEnabled());

        assertTrue(service.append(1L, "CREATE_PAYMENT", 10L, "Betalning skapad"));
        assertTrue(service.append(2L, "APPROVE_PAYMENT", 10L, "Betalning godkänd"));
        assertTrue(service.append(null, "SYSTEM", null, "a|b\\c"));

        assertEquals(3, Files.readAllLines(logFile).size());
        assertEquals(AuditSignerService.State.OK, service.verify().state());
    }

    @Test
    void utf8DescriptionIsWrittenAsUtf8() throws IOException {
        assertTrue(service().append(1L, "NOTE", 1L, "Hyra Malmö åäö"));

        assertTrue(Files.readString(logFile, StandardCharsets.UTF_8).contains("Hyra Malmö åäö"));
    }

    @Test
    void detectsTamperingWithTheLogFile() throws IOException {
        AuditSignerService service = service();
        service.append(1L, "CREATE_PAYMENT", 10L, "Betalning skapad");
        service.append(2L, "APPROVE_PAYMENT", 10L, "Betalning godkänd");
        service.append(3L, "APPROVE_PAYMENT", 11L, "Betalning godkänd");

        List<String> lines = Files.readAllLines(logFile);
        lines.set(1, lines.get(1).replace("Betalning godkänd", "Betalning NEKAD"));
        Files.write(logFile, lines);

        AuditSignerService.Integrity result = service.verify();
        assertEquals(AuditSignerService.State.BROKEN, result.state());
        assertEquals(2, result.brokenLine());
    }

    @Test
    void wrongKeyIsDetected() {
        AuditSignerService writer = service();
        writer.append(1L, "CREATE_PAYMENT", 10L, "x");

        AuditSignerService otherKey = new AuditSignerService("another-key", logFile.toString(), true);

        assertEquals(AuditSignerService.State.BROKEN, otherKey.verify().state());
    }

    @Test
    void nativeRejectsOverlongInputButJavaCutsItFirst() {
        AuditSignerService service = service();

        assertTrue(service.append(1L, "A".repeat(500), 1L, "d".repeat(5000)));
        assertEquals(AuditSignerService.State.OK, service.verify().state());
    }
}
