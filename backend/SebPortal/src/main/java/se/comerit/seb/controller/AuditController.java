package se.comerit.seb.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import se.comerit.seb.dto.AuditEntryResponse;
import se.comerit.seb.dto.MyPaymentStatusResponse;
import se.comerit.seb.dto.PaymentAuditTimelineEntryResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.AuditService;

@RestController
public class AuditController {

    private final AuditService auditService;
    private final JwtUserContext jwtUserContext;

    public AuditController(AuditService auditService,
                           JwtUserContext jwtUserContext) {
        this.auditService = auditService;
        this.jwtUserContext = jwtUserContext;
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @GetMapping("/api/audit")
    public List<AuditEntryResponse> getAuditEntries() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return auditService.getAuditEntries(user);
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @GetMapping("/api/payments/{paymentId}/audit")
    public List<PaymentAuditTimelineEntryResponse> getPaymentAuditTimeline(@PathVariable Long paymentId) {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return auditService.getPaymentAuditTimeline(user, paymentId);
    }

    @PreAuthorize("hasAnyRole('INITIATOR', 'ADMIN')")
    @GetMapping("/api/my-payments")
    public List<MyPaymentStatusResponse> getMyPaymentStatuses() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return auditService.getMyPaymentStatuses(user);
    }
}
