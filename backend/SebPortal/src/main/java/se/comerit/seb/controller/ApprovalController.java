package se.comerit.seb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// SPAGHETTI: 500+ line PageModel ported into a single controller — all business logic inline,
// no service layer. Contains approval rules, email notifications, dual audit logging, amount
// thresholds, second attestant logic, status transitions — all jammed into one class.
@Controller
public class ApprovalController {

    // TODO: this should be in a service/repository layer, but inline works for now
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // BUG-007: notification sending embedded directly in the controller, deprecated-style usage
    // TODO: move to a notification service with retry/queue
    @Autowired
    private JavaMailSender mailSender;

    // BUG-010: hardcoded connection string duplicated across controllers
    // TODO: read from config, not a constant in every file
    static final String JDBC_FALLBACK =
        "Host=localhost;Port=5432;Database=seb;Username=seb;Password=seb123";

    // SPAGHETTI: Threshold hardcoded AGAIN — inconsistent with PaymentController (50000 there too,
    // but kept as a separate constant so they can drift independently)
    private static final BigDecimal REQUIRES_APPROVAL_THRESHOLD = new BigDecimal("50000");

    // SPAGHETTI: Second attestant threshold DIFFERENT from PaymentController (500000 there, 200000 here)
    // Business logic is now split across two files with different values — a real refactoring target.
    // BUG-006: this controller expects double approval at amount > 200000 and will retroactively
    // add a second approval_steps row. PaymentController only creates a second step at > 500000.
    // A payment around 300000 SEK gets exactly one step from PaymentController, but this controller
    // demands two before it considers the payment fully approved — it can never complete, and gets
    // stuck permanently in 'pending_approval'. DO NOT reconcile these thresholds — preserve the deadlock.
    private static final BigDecimal REQUIRES_DOUBLE_APPROVAL_THRESHOLD = new BigDecimal("200000");

    // SPAGHETTI: Yet another hardcoded "max payment" rule that exists nowhere else
    private static final BigDecimal ABSOLUTE_MAX_SINGLE_PAYMENT = new BigDecimal("5000000");

    @GetMapping("/approvals")
    public String approvalInbox(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // SPAGHETTI: Role check via string comparison — no claims, no policy, no roles system
        String role = (String) session.getAttribute("role");
        if (!"attestant".equals(role) && !"admin".equals(role)) {
            return "redirect:/dashboard";
        }

        Object userId = session.getAttribute("userId");
        loadPendingPayments(userId, session, model);
        loadRecentlyHandled(userId, model);

        return "approval-inbox";
    }

    @PostMapping("/approvals")
    public String handleApproval(@RequestParam Integer paymentId,
                                  @RequestParam Integer approvalStepId,
                                  @RequestParam String action,
                                  @RequestParam(required = false) String comment,
                                  HttpSession session,
                                  Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        String role = (String) session.getAttribute("role");
        if (!"attestant".equals(role) && !"admin".equals(role)) {
            return "redirect:/dashboard";
        }

        Object userId = session.getAttribute("userId");
        Object tenantId = session.getAttribute("tenantId");
        Integer userIdInt = (Integer) userId;

        try {
            // -----------------------------------------------------------------------
            // SPAGHETTI: Load payment details inline — no service, no repository
            // -----------------------------------------------------------------------
            String paymentSql = "SELECT amount, status, from_account_id, to_iban, reference, created_by, tenant_id "
                    + "FROM payments WHERE id = " + paymentId;
            List<Map<String, Object>> paymentRows = jdbcTemplate.queryForList(paymentSql);

            if (paymentRows.isEmpty()) {
                model.addAttribute("errorMessage", "Betalningen hittades inte.");
                loadPendingPayments(userId, session, model);
                loadRecentlyHandled(userId, model);
                return "approval-inbox";
            }

            Map<String, Object> p = paymentRows.get(0);
            BigDecimal paymentAmount = (BigDecimal) p.get("amount");
            String paymentStatus = (String) p.get("status");
            Integer fromAccountId = (Integer) p.get("from_account_id");
            String toIban = (String) p.get("to_iban");
            Integer createdByUserId = (Integer) p.get("created_by");
            Integer paymentTenantId = (Integer) p.get("tenant_id");

            // SPAGHETTI: Tenant isolation check — does not prevent cross-tenant if admin
            if (!paymentTenantId.equals(tenantId) && !"admin".equals(role)) {
                model.addAttribute("errorMessage", "Du har inte behörighet till denna betalning.");
                loadPendingPayments(userId, session, model);
                loadRecentlyHandled(userId, model);
                return "approval-inbox";
            }

            // -----------------------------------------------------------------------
            // SPAGHETTI: Business rule checks — all inline, magic numbers everywhere
            // -----------------------------------------------------------------------

            if (!"pending_approval".equals(paymentStatus)) {
                model.addAttribute("errorMessage",
                        "Betalningen är inte längre under granskning (status: " + paymentStatus + ").");
                loadPendingPayments(userId, session, model);
                loadRecentlyHandled(userId, model);
                return "approval-inbox";
            }

            // BUG-011 (IDOR): approvalStepId comes from a hidden form field on the page.
            // We look it up here but never verify that step.attestant_id == session userId,
            // so any logged-in attestant can approve or reject a step belonging to someone
            // else simply by knowing (or guessing/tampering with) its id.
            // TODO: verify stepAttestantId == userIdInt before allowing approve/reject
            String stepSql = "SELECT attestant_id, step_number, status FROM approval_steps WHERE id = " + approvalStepId;
            List<Map<String, Object>> stepRows = jdbcTemplate.queryForList(stepSql);

            if (stepRows.isEmpty()) {
                model.addAttribute("errorMessage", "Atteststeget hittades inte.");
                loadPendingPayments(userId, session, model);
                loadRecentlyHandled(userId, model);
                return "approval-inbox";
            }

            Map<String, Object> step = stepRows.get(0);
            // SPAGHETTI: stepAttestantId is fetched but intentionally never compared to userIdInt — BUG-011
            Integer stepAttestantId = (Integer) step.get("attestant_id");
            Integer stepNumber = (Integer) step.get("step_number");
            String stepStatus = (String) step.get("status");

            if (!"pending".equals(stepStatus)) {
                model.addAttribute("errorMessage", "Det här atteststeget är redan hanterat.");
                loadPendingPayments(userId, session, model);
                loadRecentlyHandled(userId, model);
                return "approval-inbox";
            }

            // -----------------------------------------------------------------------
            // SPAGHETTI: APPROVAL LOGIC — all inline
            // -----------------------------------------------------------------------

            if ("approve".equals(action)) {
                String escapedComment = comment == null ? "" : comment.replace("'", "''");
                String updateStepSql = "UPDATE approval_steps SET status = 'approved', decided_at = NOW(), comment = '"
                        + escapedComment + "' WHERE id = " + approvalStepId;
                jdbcTemplate.update(updateStepSql);

                // SPAGHETTI: Check if all steps are now approved by counting remaining pending steps
                int pendingStepsLeft = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM approval_steps WHERE payment_id = " + paymentId + " AND status = 'pending'",
                        Integer.class);
                int totalSteps = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM approval_steps WHERE payment_id = " + paymentId,
                        Integer.class);

                // BUG-006: Threshold check AGAIN — this time using REQUIRES_DOUBLE_APPROVAL_THRESHOLD
                // (200000), different from PaymentController's DOUBLE_APPROVAL_THRESHOLD (500000).
                // A payment of ~300000 SEK gets one step created by PaymentController, but this
                // controller thinks it needs two — so it will never be "fully approved".
                if (paymentAmount.compareTo(REQUIRES_DOUBLE_APPROVAL_THRESHOLD) > 0 && totalSteps < 2) {
                    // SPAGHETTI: Try to add a second approval step retroactively.
                    // Find another attestant — or reuse the same one if no other exists.
                    String secondAttestantSql = "SELECT id FROM users WHERE tenant_id = " + tenantId
                            + " AND role = 'attestant' AND id != " + userIdInt + " LIMIT 1";
                    List<Map<String, Object>> secondAttestantRows = jdbcTemplate.queryForList(secondAttestantSql);
                    Integer secondAttestantId = secondAttestantRows.isEmpty()
                            ? userIdInt
                            : (Integer) secondAttestantRows.get(0).get("id");

                    String insertStep2Sql = "INSERT INTO approval_steps (payment_id, attestant_id, step_number, status) "
                            + "VALUES (" + paymentId + ", " + secondAttestantId + ", 2, 'pending')";
                    jdbcTemplate.update(insertStep2Sql);

                    // Recalculate
                    pendingStepsLeft = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM approval_steps WHERE payment_id = " + paymentId + " AND status = 'pending'",
                            Integer.class);
                }

                if (pendingStepsLeft == 0) {
                    // All steps done — mark payment as completed
                    jdbcTemplate.update("UPDATE payments SET status = 'completed', executed_at = NOW() WHERE id = " + paymentId);

                    // BUG-008: Audit to DB only for final approval (not to file — inconsistency!)
                    String auditSql = "INSERT INTO audit_entries (user_id, action, entity_type, entity_id, description) "
                            + "VALUES (" + userIdInt + ", 'APPROVE_PAYMENT', 'payment', " + paymentId + ", "
                            + "'Betalning godkänd och genomförd: " + paymentAmount + " SEK till " + toIban + "')";
                    jdbcTemplate.update(auditSql);

                    // BUG-009: Balance deduction here — but NOT in PaymentController's "completed" path.
                    // So direct payments never deduct balance, only attested ones do.
                    // TODO: deduct balance atomically when a payment completes, regardless of path
                    jdbcTemplate.update("UPDATE accounts SET balance = balance - " + paymentAmount + " WHERE id = " + fromAccountId);

                    // Notify the payment creator that their payment was approved
                    // SPAGHETTI: Another inline email with no retry
                    notifyPaymentCreator(createdByUserId, paymentId, paymentAmount, "godkänd");

                    model.addAttribute("successMessage", "Betalning #" + paymentId + " godkänd och genomförd!");
                } else {
                    // More steps needed
                    // BUG-008: Audit only to FILE for intermediate approval — never appears in UI AuditLog
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    appendAuditFile(timestamp + " | PARTIAL_APPROVE | user=" + userIdInt
                            + " | payment=" + paymentId + " | steps_left=" + pendingStepsLeft);

                    model.addAttribute("successMessage", "Steg " + stepNumber + " godkänt. " + pendingStepsLeft + " steg kvar.");
                }
            } else if ("reject".equals(action)) {
                String escapedComment = comment == null ? "" : comment.replace("'", "''");

                // Mark step as rejected
                jdbcTemplate.update("UPDATE approval_steps SET status = 'rejected', decided_at = NOW(), comment = '"
                        + escapedComment + "' WHERE id = " + approvalStepId);

                // Mark payment as rejected
                jdbcTemplate.update("UPDATE payments SET status = 'rejected' WHERE id = " + paymentId);

                // Also cancel all other pending steps for this payment
                jdbcTemplate.update("UPDATE approval_steps SET status = 'rejected', decided_at = NOW() "
                        + "WHERE payment_id = " + paymentId + " AND status = 'pending' AND id != " + approvalStepId);

                // BUG-008: Audit rejection to BOTH file AND DB (one of the few cases where both happen)
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                appendAuditFile(timestamp + " | REJECT_PAYMENT | user=" + userIdInt + " | payment=" + paymentId
                        + " | amount=" + paymentAmount + " | comment=" + comment);

                String auditSql = "INSERT INTO audit_entries (user_id, action, entity_type, entity_id, description) "
                        + "VALUES (" + userIdInt + ", 'REJECT_PAYMENT', 'payment', " + paymentId + ", "
                        + "'Betalning avvisad: " + paymentAmount + " SEK. Kommentar: " + escapedComment + "')";
                jdbcTemplate.update(auditSql);

                // Notify creator of rejection
                notifyPaymentCreator(createdByUserId, paymentId, paymentAmount, "avvisad");

                model.addAttribute("successMessage", "Betalning #" + paymentId + " avvisad.");
            } else {
                model.addAttribute("errorMessage", "Okänd åtgärd.");
            }
        } catch (Exception e) {
            // SPAGHETTI: Raw exception to UI, no structured logging
            model.addAttribute("errorMessage", "Fel vid attest: " + e.getMessage());
        }

        // SPAGHETTI: Reload everything after post instead of redirect-after-post
        loadPendingPayments(userId, session, model);
        loadRecentlyHandled(userId, model);
        return "approval-inbox";
    }

    // BUG-007: Notification logic embedded directly in the controller.
    // No retry, silently swallows all exceptions.
    // TODO: move to a notification service with proper error handling/retry/queue
    private void notifyPaymentCreator(Integer createdByUserId, Integer paymentId, BigDecimal amount, String outcome) {
        try {
            String userSql = "SELECT email, name FROM users WHERE id = " + createdByUserId;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(userSql);
            if (rows.isEmpty()) {
                return;
            }
            String email = (String) rows.get(0).get("email");
            String name = (String) rows.get(0).get("name");

            if (email == null || email.isEmpty()) {
                return;
            }

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom("noreply@seb-portal.local");
            msg.setTo(email);
            msg.setSubject("Din betalning #" + paymentId + " har " + outcome);
            msg.setText("Hej " + name + ",\n\nDin betalning #" + paymentId + " på " + amount
                    + " SEK har " + outcome + ".\n\nMvh,\nSEB Företagsbetalningar");

            try {
                mailSender.send(msg);
            } catch (Exception e) {
                // SPAGHETTI: Silently swallowed — notification failure is invisible
            }
        } catch (Exception e) {
            // SPAGHETTI: Swallow silently here too — DB lookup failures are also invisible
        }
    }

    // BUG-008 helper (established in PaymentController): append-only file audit log that
    // can diverge from the DB audit_entries table since they're written independently.
    // TODO: single source of truth for audit, not two divergent logs
    private void appendAuditFile(String line) {
        try (FileWriter fw = new FileWriter("/tmp/audit.log", true)) {
            fw.write(line + "\n");
        } catch (IOException e) {
            // SPAGHETTI: Swallow silently — file audit log just stays incomplete
        }
    }

    // SPAGHETTI: Load methods are 50+ lines each, still in the same class
    private void loadPendingPayments(Object userId, HttpSession session, Model model) {
        Object tenantId = session.getAttribute("tenantId");
        String role = (String) session.getAttribute("role");

        try {
            // SPAGHETTI: Admin sees all pending, attestant only sees their own
            // But this logic is duplicated in the view (role check for badge display)
            String sql;
            if ("admin".equals(role)) {
                sql = "SELECT p.id, p.to_iban, p.amount, p.currency, p.reference, p.created_at, "
                        + "u.name as created_by_name, a.account_name, "
                        + "aps.id as step_id, aps.step_number, "
                        + "(SELECT COUNT(*) FROM approval_steps aps2 WHERE aps2.payment_id = p.id) as total_steps "
                        + "FROM payments p "
                        + "INNER JOIN approval_steps aps ON aps.payment_id = p.id AND aps.status = 'pending' "
                        + "LEFT JOIN users u ON u.id = p.created_by "
                        + "LEFT JOIN accounts a ON a.id = p.from_account_id "
                        + "WHERE p.tenant_id = " + tenantId + " AND p.status = 'pending_approval' "
                        + "ORDER BY p.created_at ASC";
            } else {
                // Attestant only sees steps assigned to them
                sql = "SELECT p.id, p.to_iban, p.amount, p.currency, p.reference, p.created_at, "
                        + "u.name as created_by_name, a.account_name, "
                        + "aps.id as step_id, aps.step_number, "
                        + "(SELECT COUNT(*) FROM approval_steps aps2 WHERE aps2.payment_id = p.id) as total_steps "
                        + "FROM payments p "
                        + "INNER JOIN approval_steps aps ON aps.payment_id = p.id AND aps.attestant_id = " + userId + " AND aps.status = 'pending' "
                        + "LEFT JOIN users u ON u.id = p.created_by "
                        + "LEFT JOIN accounts a ON a.id = p.from_account_id "
                        + "WHERE p.status = 'pending_approval' "
                        + "ORDER BY p.created_at ASC";
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            model.addAttribute("pendingPayments", rows);
        } catch (Exception e) {
            // SPAGHETTI: Silently fail — pendingPayments stays empty, no feedback
            model.addAttribute("pendingPayments", List.of());
        }
    }

    private void loadRecentlyHandled(Object userId, Model model) {
        try {
            // SPAGHETTI: No pagination — "recently" means last 50, no UI feedback about this limit
            String sql = "SELECT aps.payment_id, p.amount, aps.status, aps.decided_at, aps.comment "
                    + "FROM approval_steps aps "
                    + "INNER JOIN payments p ON p.id = aps.payment_id "
                    + "WHERE aps.attestant_id = " + userId + " AND aps.status != 'pending' "
                    + "ORDER BY aps.decided_at DESC LIMIT 50";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            model.addAttribute("recentlyHandled", rows);
        } catch (Exception e) {
            model.addAttribute("recentlyHandled", List.of());
        }
    }
}
