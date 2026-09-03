package se.comerit.seb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

// SPAGHETTI: ported straight from AuditLog.cshtml.cs — all data access inline, no service layer.
@Controller
public class AuditController {

    // TODO: this should be in a service/repository layer, but inline works for now
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // BUG-010: hardcoded connection string duplicated across controllers (fifth occurrence)
    // TODO: read from config, not a constant in every file
    static final String JDBC_FALLBACK =
        "Host=localhost;Port=5432;Database=seb;Username=seb;Password=seb123";

    @GetMapping("/audit")
    public String auditLog(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // SPAGHETTI: tenantId is fetched from session but never actually used to filter the
        // query below — audit_entries has no tenant_id column, so we can't filter properly.
        // A user from a different tenant could see this data if they guessed the URL.
        Object tenantId = session.getAttribute("tenantId");

        // BUG-008: Reads from DB only — but the audit log is ALSO written to /tmp/audit.log
        // (BatchController's BATCH_PAYMENT, ApprovalController's PARTIAL_APPROVE, etc).
        // Those file-only entries never exist in audit_entries, so they never appear here.
        // This makes the audit log fundamentally incomplete in the UI.
        try {
            // SPAGHETTI: No tenant filtering on audit_entries — shows ALL users' entries
            String sql = "SELECT ae.id, ae.action, ae.entity_type, ae.entity_id, ae.description, ae.created_at, "
                    + "COALESCE(u.name, 'Systemet') as user_name "
                    + "FROM audit_entries ae "
                    + "LEFT JOIN users u ON u.id = ae.user_id "
                    + "ORDER BY ae.created_at DESC LIMIT 200";

            List<Map<String, Object>> entries = jdbcTemplate.queryForList(sql);
            model.addAttribute("entries", entries);
        } catch (Exception e) {
            // SPAGHETTI: Raw exception in UI
            model.addAttribute("errorMessage", "Kunde inte hämta loggdata: " + e.getMessage());
        }

        return "audit-log";
    }
}
