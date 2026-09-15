package se.comerit.seb.controller;

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

    @GetMapping("/audit")
    public String auditLog(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            AuthenticatedUserContext user = sessionUserContext.requireAdminOrAttestant(session);
            List<AuditEntryResponse> entries = auditService.getAuditEntries(user);
            model.addAttribute("entries", entries);
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Kunde inte hämta loggdata: " + e.getMessage());
        }

        return "audit-log";
    }

    @GetMapping("/api/audit")
    @ResponseBody
    public List<AuditEntryResponse> getAuditEntries(HttpSession session) {
        AuthenticatedUserContext user = sessionUserContext.requireAdminOrAttestant(session);
        return auditService.getAuditEntries(user);
    }

    @GetMapping("/api/payments/{paymentId}/audit")
    @ResponseBody
    public List<PaymentAuditTimelineEntryResponse> getPaymentAuditTimeline(
            @PathVariable Long paymentId,
            HttpSession session
    ) {
        AuthenticatedUserContext user = sessionUserContext.requireAdmin(session);
        return auditService.getPaymentAuditTimeline(user, paymentId);
    }
}
