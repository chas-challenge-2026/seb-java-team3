package se.comerit.seb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import java.util.List;

import se.comerit.seb.dto.AuditEntryResponse;
import se.comerit.seb.service.AuditService;

@Controller
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit")
    public String auditLog(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        Long tenantId = (Long) session.getAttribute("tenantId");
        if (tenantId == null) {
            model.addAttribute("errorMessage", "Ingen tenant kopplad till användaren.");
            return "audit-log";
        }

        try {
            List<AuditEntryResponse> entries = auditService.getAuditEntries(tenantId);
            model.addAttribute("entries", entries);
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Kunde inte hämta loggdata: " + e.getMessage());
        }

        return "audit-log";
    }

    @GetMapping("/api/audit")
    @ResponseBody
    public List<AuditEntryResponse> getAuditEntries(HttpSession session) {
        if (session.getAttribute("userId") == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Not logged in"
            );
        }

        Long tenantId = (Long) session.getAttribute("tenantId");

        if (tenantId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "No tenant associated with user"
            );
        }

        return auditService.getAuditEntries(tenantId);
    }
}
