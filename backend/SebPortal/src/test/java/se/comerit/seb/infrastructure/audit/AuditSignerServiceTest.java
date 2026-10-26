package se.comerit.seb.infrastructure.audit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditSignerServiceTest {

    private static final String KEY = "a-real-key";
    private static final String PATH = System.getProperty("java.io.tmpdir") + "/audit-signer-unit/audit.log";

    private final AuditSignerLib lib = mock(AuditSignerLib.class);

    private AuditSignerService service() {
        return new AuditSignerService(lib, KEY, PATH, false);
    }

    // ----- startup / configuration -----

    @Test
    void withoutLibrary_isDisabledAndAppendDoesNothing() {
        AuditSignerService service = new AuditSignerService(null, KEY, PATH, false);

        assertFalse(service.isEnabled());
        assertFalse(service.append(1L, "CREATE_PAYMENT", 2L, "x"));
        assertEquals(0, service.getFailedAppends());
        assertEquals(AuditSignerService.State.DISABLED, service.verify().state());
    }

    @Test
    void withoutKeyOrPath_isDisabled() {
        assertFalse(new AuditSignerService(lib, "", PATH, false).isEnabled());
        assertFalse(new AuditSignerService(lib, KEY, " ", false).isEnabled());
        assertFalse(new AuditSignerService(lib, null, null, false).isEnabled());
    }

    @Test
    void required_withoutLibrary_failsStartup() {
        assertThrows(IllegalStateException.class, () -> new AuditSignerService(null, KEY, PATH, true));
    }

    @Test
    void required_withDevKey_failsStartup() {
        assertThrows(IllegalStateException.class,
                () -> new AuditSignerService(lib, AuditSignerService.DEV_SIGNING_KEY, PATH, true));
    }

    @Test
    void required_withoutKey_failsStartup() {
        assertThrows(IllegalStateException.class, () -> new AuditSignerService(lib, "", PATH, true));
    }

    @Test
    void notRequired_withDevKey_isAllowed() {
        assertTrue(new AuditSignerService(lib, AuditSignerService.DEV_SIGNING_KEY, PATH, false).isEnabled());
    }

    // ----- append -----

    @Test
    void append_passesArgumentsToNativeLibrary() {
        when(lib.audit_append(any(), any(), anyInt(), any(), anyInt(), any())).thenReturn(0);

        assertTrue(service().append(42L, "APPROVE_PAYMENT", 7L, "Betalning godkänd"));

        verify(lib).audit_append(PATH, KEY, 42, "APPROVE_PAYMENT", 7, "Betalning godkänd");
    }

    @Test
    void append_nullUserAndEntity_becomeZero() {
        when(lib.audit_append(any(), any(), anyInt(), any(), anyInt(), any())).thenReturn(0);

        assertTrue(service().append(null, "LOGIN", null, null));

        verify(lib).audit_append(PATH, KEY, 0, "LOGIN", 0, "");
    }

    @Test
    void append_blankAction_becomesUnknown() {
        when(lib.audit_append(any(), any(), anyInt(), any(), anyInt(), any())).thenReturn(0);

        assertTrue(service().append(1L, "  ", 1L, "x"));

        verify(lib).audit_append(PATH, KEY, 1, "UNKNOWN", 1, "x");
    }

    @Test
    void append_replacesLineBreaksAndNul() {
        when(lib.audit_append(any(), any(), anyInt(), any(), anyInt(), any())).thenReturn(0);

        assertTrue(service().append(1L, "NOTE", 1L, "rad ett\r\nrad två\nrad tre\0slut"));

        verify(lib).audit_append(PATH, KEY, 1, "NOTE", 1, "rad ett rad två rad tre slut");
    }

    @Test
    void append_cutsLongDescriptionAtByteLimitWithoutSplittingCharacters() {
        when(lib.audit_append(any(), any(), anyInt(), any(), anyInt(), any())).thenReturn(0);
        String longDescription = "å".repeat(2000);

        assertTrue(service().append(1L, "NOTE", 1L, longDescription));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(lib).audit_append(any(), any(), anyInt(), any(), anyInt(), sent.capture());
        assertEquals(AuditSignerService.MAX_DESCRIPTION_BYTES, sent.getValue().getBytes(StandardCharsets.UTF_8).length);
        assertTrue(sent.getValue().chars().allMatch(c -> c == 'å'));
    }

    @Test
    void append_cutsLongAction() {
        when(lib.audit_append(any(), any(), anyInt(), any(), anyInt(), any())).thenReturn(0);

        assertTrue(service().append(1L, "A".repeat(100), 1L, "x"));

        verify(lib).audit_append(PATH, KEY, 1, "A".repeat(AuditSignerService.MAX_ACTION_BYTES), 1, "x");
    }

    @Test
    void append_nativeFailure_isCountedAndDoesNotThrow() {
        when(lib.audit_append(any(), any(), anyInt(), any(), anyInt(), any())).thenReturn(-1);
        AuditSignerService service = service();

        assertFalse(service.append(1L, "A", 1L, "x"));
        assertFalse(service.append(1L, "A", 1L, "x"));

        assertEquals(2, service.getFailedAppends());
    }

    @Test
    void append_nativeException_isCountedAndDoesNotThrow() {
        when(lib.audit_append(any(), any(), anyInt(), any(), anyInt(), any()))
                .thenThrow(new UnsatisfiedLinkError("boom"));
        AuditSignerService service = service();

        assertFalse(service.append(1L, "A", 1L, "x"));

        assertEquals(1, service.getFailedAppends());
    }

    @Test
    void append_idOutsideIntRange_isRejectedAndCounted() {
        AuditSignerService service = service();

        assertFalse(service.append(1L, "A", 5_000_000_000L, "x"));

        assertEquals(1, service.getFailedAppends());
        verifyNoInteractions(lib);
    }

    // ----- verify -----

    @Test
    void verify_mapsNativeResults() {
        AuditSignerService service = service();

        when(lib.audit_verify(PATH, KEY)).thenReturn(-1);
        assertEquals(new AuditSignerService.Integrity(AuditSignerService.State.OK, 0), service.verify());

        when(lib.audit_verify(PATH, KEY)).thenReturn(3);
        assertEquals(new AuditSignerService.Integrity(AuditSignerService.State.BROKEN, 3), service.verify());

        when(lib.audit_verify(PATH, KEY)).thenReturn(0);
        assertEquals(AuditSignerService.State.ERROR, service.verify().state());

        when(lib.audit_verify(PATH, KEY)).thenThrow(new UnsatisfiedLinkError("boom"));
        assertEquals(AuditSignerService.State.ERROR, service.verify().state());
    }

    @Test
    void integrity_reusesRecentResult() {
        when(lib.audit_verify(PATH, KEY)).thenReturn(-1);
        AuditSignerService service = service();

        service.integrity();
        service.integrity();
        service.integrity();

        verify(lib, times(1)).audit_verify(PATH, KEY);
    }

    @Test
    void verify_alwaysRefreshes() {
        when(lib.audit_verify(PATH, KEY)).thenReturn(-1);
        AuditSignerService service = service();

        service.verify();
        service.verify();

        verify(lib, times(2)).audit_verify(PATH, KEY);
    }
}
