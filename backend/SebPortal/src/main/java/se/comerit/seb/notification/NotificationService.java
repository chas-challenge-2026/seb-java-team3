package se.comerit.seb.notification;

import java.math.BigDecimal;

public interface NotificationService {
    void paymentRequiresApproval(Integer attestantId, Integer paymentId, BigDecimal amount);
}
