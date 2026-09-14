package se.comerit.seb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.service.AuditService;

@Controller
public class AuditController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    static final String JDBC_FALLBACK =
            "Host=localhost;Port=5432;Database=seb;Username=seb;Password=seb123";

    @GetMapping("/audit")
    public String auditLog(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        Object tenantId = session.getAttribute("tenantId");

        try {
            String sql = "SELECT ae.id, ae.action, ae.entity_type, ae.entity_id, ae.description, ae.created_at, "
                    + "COALESCE(u.name, 'Systemet') as user_name "
                    + "FROM audit_entries ae "
                    + "LEFT JOIN users u ON u.id = ae.user_id "
                    + "ORDER BY ae.created_at DESC LIMIT 200";

            List<Map<String, Object>> entries = jdbcTemplate.queryForList(sql);
            model.addAttribute("entries", entries);
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Kunde inte hämta loggdata: " + e.getMessage());
        }

        return "audit-log";
    }

    @GetMapping("/api/audit")
    @ResponseBody
    public List<AuditEntry> getAuditEntries(HttpSession session) {
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