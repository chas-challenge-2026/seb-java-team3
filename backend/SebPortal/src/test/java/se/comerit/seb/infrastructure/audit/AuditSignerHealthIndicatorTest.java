package se.comerit.seb.infrastructure.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditSignerHealthIndicatorTest {

    private final AuditSignerService signer = mock(AuditSignerService.class);
    private final AuditSignerHealthIndicator indicator = new AuditSignerHealthIndicator(signer);

    private void given(AuditSignerService.State state, int brokenLine, long failedAppends) {
        when(signer.integrity()).thenReturn(new AuditSignerService.Integrity(state, brokenLine));
        when(signer.getFailedAppends()).thenReturn(failedAppends);
    }

    @Test
    void intactChain_isUp() {
        given(AuditSignerService.State.OK, 0, 0);

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void brokenChain_isDownWithLine() {
        given(AuditSignerService.State.BROKEN, 5, 0);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(5, health.getDetails().get("firstBrokenLine"));
    }

    @Test
    void unreadableLog_isDown() {
        given(AuditSignerService.State.ERROR, 0, 0);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    void failedAppends_makeHealthDownEvenIfChainIsIntact() {
        given(AuditSignerService.State.OK, 0, 2);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(2L, health.getDetails().get("failedAppends"));
    }

    @Test
    void disabledSigning_isUnknownSoOverallHealthIsNotDown() {
        given(AuditSignerService.State.DISABLED, 0, 0);

        assertEquals(Status.UNKNOWN, indicator.health().getStatus());
    }
}
