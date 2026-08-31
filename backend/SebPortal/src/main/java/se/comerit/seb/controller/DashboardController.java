package se.comerit.seb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

// SPAGHETTI: All data access directly in the controller — no services, no repositories
@Controller
public class DashboardController {

    // TODO: this should be in a service/repository layer, but inline works for now
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        Object userId = session.getAttribute("userId");
        String userName = (String) session.getAttribute("userName");
        String role = (String) session.getAttribute("role");
        Object tenantId = session.getAttribute("tenantId");

        // SPAGHETTI: Three separate DB round-trips, no batching

        // Query 1: tenant name
        String tenantSql = "SELECT name FROM tenants WHERE id = " + tenantId;
        List<Map<String, Object>> tenantRows = jdbcTemplate.queryForList(tenantSql);
        String tenantName = tenantRows.isEmpty() ? "Okänt företag" : (String) tenantRows.get(0).get("name");

        // Query 2: accounts — SPAGHETTI: no filtering beyond tenant, fetches ALL accounts
        String accountsSql = "SELECT id, account_name, iban, balance, currency FROM accounts WHERE tenant_id = "
                + tenantId + " ORDER BY id";
        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(accountsSql);

        // Query 3: recent payments — SPAGHETTI: no pagination
        String paymentsSql = "SELECT p.id, p.to_iban, p.amount, p.currency, p.reference, p.status, p.created_at "
                + "FROM payments p WHERE p.tenant_id = " + tenantId
                + " ORDER BY p.created_at DESC LIMIT 20";
        List<Map<String, Object>> recentPayments = jdbcTemplate.queryForList(paymentsSql);

        // Query 4: pending approvals for this user (if attestant/admin)
        // SPAGHETTI: Role check via string comparison, no claims/roles system
        List<Map<String, Object>> pendingApprovals = null;
        if ("attestant".equals(role) || "admin".equals(role)) {
            String approvalsSql = "SELECT p.id, p.to_iban, p.amount, p.currency, p.reference, p.status, p.created_at "
                    + "FROM payments p "
                    + "INNER JOIN approval_steps aps ON aps.payment_id = p.id "
                    + "WHERE aps.attestant_id = " + userId + " AND aps.status = 'pending' "
                    + "ORDER BY p.created_at ASC";
            pendingApprovals = jdbcTemplate.queryForList(approvalsSql);
        }

        model.addAttribute("userName", userName);
        model.addAttribute("userRole", role);
        model.addAttribute("tenantName", tenantName);
        model.addAttribute("accounts", accounts);
        model.addAttribute("recentPayments", recentPayments);
        model.addAttribute("pendingApprovals", pendingApprovals);

        return "dashboard";
    }
}
