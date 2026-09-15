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
import se.comerit.seb.dto.PaymentAuditTimelineEntryResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.SessionUserContext;
import se.comerit.seb.service.AuditService;

@Controller
public class AuditController {

    private final AuditService auditService;
    private final SessionUserContext sessionUserContext;

    public AuditController(AuditService auditService,
                           SessionUserContext sessionUserContext) {
        this.auditService = auditService;
        this.sessionUserContext = sessionUserContext;
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
    public List<AuditEntryResponse> getAuditEntries(HttpSession session) {
        AuthenticatedUserContext user = sessionUserContext.requireAuthenticated(session);
        return auditService.getAuditEntries(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/payments/{paymentId}/audit")
    @ResponseBody
    public List<PaymentAuditTimelineEntryResponse> getPaymentAuditTimeline(
            @PathVariable Long paymentId,
            HttpSession session
    ) {
        AuthenticatedUserContext user = sessionUserContext.requireAuthenticated(session);
        return auditService.getPaymentAuditTimeline(user, paymentId);
    }
}
