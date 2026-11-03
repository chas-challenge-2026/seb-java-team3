package se.comerit.seb.infrastructure.audit;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the integrity of the signed audit log under /actuator/health.
 * DOWN if the chain is broken or unreadable, or if a signed entry could not be written since startup.
 * UNKNOWN (does not turn the whole health DOWN) when signing is disabled.
 */
@Component("auditSigner")
public class AuditSignerHealthIndicator implements HealthIndicator {

    private final AuditSignerService signer;

    public AuditSignerHealthIndicator(AuditSignerService signer) {
        this.signer = signer;
    }

    @Override
    public Health health() {
        AuditSignerService.Integrity integrity = signer.integrity();
        long failedAppends = signer.getFailedAppends();

        return switch (integrity.state()) {
            case DISABLED -> Health.unknown().withDetail("signing", "disabled").build();
            case BROKEN -> Health.down()
                    .withDetail("reason", "chain broken")
                    .withDetail("firstBrokenLine", integrity.brokenLine())
                    .build();
            case ERROR -> Health.down().withDetail("reason", "log unreadable").build();
            case OK -> failedAppends > 0
                    ? Health.down()
                            .withDetail("reason", "signed entries missing from the log")
                            .withDetail("failedAppends", failedAppends)
                            .build()
                    : Health.up().build();
        };
    }
}
