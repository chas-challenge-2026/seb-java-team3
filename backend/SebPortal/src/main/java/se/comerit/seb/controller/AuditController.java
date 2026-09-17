package se.comerit.seb.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpSession;
import java.util.List;

import se.comerit.seb.dto.AuditEntryResponse;
import se.comerit.seb.dto.MyPaymentStatusResponse;
import se.comerit.seb.dto.PaymentAuditTimelineEntryResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.SessionUserContext;
import se.comerit.seb.service.AuditService;

@Controller
public class AuditController {

    private final AuditService auditService;
    private final SessionUserContext sessionUserContext;
    private final JwtUserContext jwtUserContext;

    public AuditController(AuditService auditService,
                           SessionUserContext sessionUserContext,
                           JwtUserContext jwtUserContext) {
        this.auditService = auditService;
        this.sessionUserContext = sessionUserContext;
        this.jwtUserContext = jwtUserContext;
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @GetMapping("/audit")
    public String auditLog(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            AuthenticatedUserContext user = sessionUserContext.requireAuthenticated(session);
            List<AuditEntryResponse> entries = auditService.getAuditEntries(user);
            model.addAttribute("entries", entries);
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Kunde inte hämta loggdata: " + e.getMessage());
        }

        return "audit-log";
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @GetMapping("/api/audit")
    @ResponseBody
    public List<AuditEntryResponse> getAuditEntries() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return auditService.getAuditEntries(user);
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @GetMapping("/api/payments/{paymentId}/audit")
    @ResponseBody
    public List<PaymentAuditTimelineEntryResponse> getPaymentAuditTimeline(@PathVariable Long paymentId) {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return auditService.getPaymentAuditTimeline(user, paymentId);
    }

    @PreAuthorize("hasAnyRole('INITIATOR', 'ADMIN')")
    @GetMapping("/api/my-payments")
    @ResponseBody
    public List<MyPaymentStatusResponse> getMyPaymentStatuses() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return auditService.getMyPaymentStatuses(user);
    }
}
