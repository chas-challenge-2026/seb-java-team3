package se.comerit.seb.notification;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AsyncNotificationService implements NotificationService {

    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(multiplier = 2))
    @Override
    public void paymentRequiresApproval(Integer attestantId, Integer paymentId, BigDecimal amount) {
        // Scaffold for v2 notification queue/retry. Existing controllers still use v1 mail flow.
    }
}
