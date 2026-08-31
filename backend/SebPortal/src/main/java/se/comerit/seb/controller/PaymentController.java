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
import se.comerit.seb.config.PaymentThresholdProperties;
import se.comerit.seb.service.AuditService;

import javax.servlet.http.HttpSession;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// SPAGHETTI: All data access directly in the controller — no services, no repositories
@Controller
public class PaymentController {

    // TODO: this should be in a service/repository layer, but inline works for now
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // BUG-007: notification sending embedded directly in the controller, deprecated-style usage
    // TODO: move to a notification service with retry/queue
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PaymentThresholdProperties paymentThresholds;

    @Autowired
    private AuditService auditService;

    // BUG-003: IBAN validation regex only — no MOD97 checksum validation at all
    // SE IBANs are 24 chars but this accepts 11-30 alphanumeric chars after the 4-char prefix
    // TODO: implement proper MOD97 IBAN checksum validation
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

    @GetMapping("/payments/new")
    public String newPaymentForm(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        Object tenantId = session.getAttribute("tenantId");
        loadAccounts(tenantId, model);
        return "new-payment";
    }

    @PostMapping("/payments/new")
    public String createPayment(@RequestParam Integer fromAccountId,
                                 @RequestParam String toIban,
                                 @RequestParam BigDecimal amount,
                                 @RequestParam(required = false) String reference,
                                 HttpSession session,
                                 Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        Object userId = session.getAttribute("userId");
        Object tenantId = session.getAttribute("tenantId");

        model.addAttribute("toIban", toIban);
        model.addAttribute("amount", amount);
        model.addAttribute("reference", reference);
        loadAccounts(tenantId, model);

        // BUG-003: regex-only IBAN validation, no MOD97 checksum — strip spaces before
        // validating but (like the .NET original) we forget to strip them again before saving
        String ibanStripped = toIban != null ? toIban.replace(" ", "") : "";
        if (!IBAN_PATTERN.matcher(ibanStripped).matches()) {
            model.addAttribute("errorMessage", "Ogiltigt IBAN-format. Kontrollera att du angett rätt format.");
            return "new-payment";
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            model.addAttribute("errorMessage", "Beloppet måste vara större än 0.");
            return "new-payment";
        }

        try {
            // Verify account belongs to this tenant — SPAGHETTI: no proper ownership check,
            // tenant_id comparison is the only guard and is bypassable
            String accountSql = "SELECT tenant_id FROM accounts WHERE id = " + fromAccountId;
            List<Map<String, Object>> accountRows = jdbcTemplate.queryForList(accountSql);
            Integer accountTenantId = accountRows.isEmpty() ? -1 : (Integer) accountRows.get(0).get("tenant_id");

            if (!accountTenantId.equals(tenantId)) {
                model.addAttribute("errorMessage", "Du har inte behörighet till det kontot.");
                return "new-payment";
            }

            String status;
            if (amount.compareTo(paymentThresholds.getApprovalThreshold()) > 0) {
                status = "pending_approval";
            } else {
                // BUG-009: Immediate "completion" with no actual funds check — balance is NOT
                // deducted here. Deduction only happens in the approval flow (ApprovalController).
                // TODO: deduct balance atomically when a payment completes, regardless of path
                status = "completed";
            }

            // SPAGHETTI: Raw SQL insert, no ORM, no transaction around the whole flow
            String insertSql = "INSERT INTO payments (tenant_id, from_account_id, to_iban, amount, currency, reference, status, created_by, created_at) "
                    + "VALUES (" + tenantId + ", " + fromAccountId + ", '" + ibanStripped + "', " + amount
                    + ", 'SEK', '" + reference + "', '" + status + "', " + userId + ", NOW()) RETURNING id";
            Integer paymentId = jdbcTemplate.queryForObject(insertSql, Integer.class);

            auditService.record((Integer) userId, "CREATE_PAYMENT", paymentId,
                    "Skapade betalning " + amount + " SEK till " + ibanStripped);

            // BUG-008 (established here, fully realized in Task 7): ALSO write to file —
            // dual audit log, already diverges from the DB log
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            appendAuditFile(timestamp + " | CREATE_PAYMENT | user=" + userId + " | payment=" + paymentId
                    + " | amount=" + amount + " | to=" + ibanStripped);

            if ("pending_approval".equals(status)) {
                // Find an attestant for this tenant — SPAGHETTI: just grabs the first one
                String attestantSql = "SELECT id FROM users WHERE tenant_id = " + tenantId + " AND role = 'attestant' LIMIT 1";
                List<Map<String, Object>> attestantRows = jdbcTemplate.queryForList(attestantSql);
                Integer attestantId = attestantRows.isEmpty() ? 0 : (Integer) attestantRows.get(0).get("id");

                if (attestantId > 0) {
                    // Create approval step
                    String stepSql = "INSERT INTO approval_steps (payment_id, attestant_id, step_number, status) "
                            + "VALUES (" + paymentId + ", " + attestantId + ", 1, 'pending')";
                    jdbcTemplate.update(stepSql);

                    if (amount.compareTo(paymentThresholds.getDoubleApprovalThreshold()) > 0) {
                        // SPAGHETTI: Grab SAME attestant as step 1 if no second one exists
                        String step2Sql = "INSERT INTO approval_steps (payment_id, attestant_id, step_number, status) "
                                + "VALUES (" + paymentId + ", " + attestantId + ", 2, 'pending')";
                        jdbcTemplate.update(step2Sql);
                    }

                    // SPAGHETTI: Send notification inline — no queue, no retry
                    sendNotification(attestantId, paymentId, amount);
                }

                model.addAttribute("successMessage",
                        "Betalning " + paymentId + " skapad och skickad för attest (belopp: " + amount + " SEK).");
            } else {
                // SPAGHETTI: Balance not actually deducted — this is a bug (BUG-009)
                model.addAttribute("successMessage", "Betalning " + paymentId + " genomförd (" + amount + " SEK).");
            }
        } catch (Exception e) {
            // SPAGHETTI: Raw exception to UI
            model.addAttribute("errorMessage", "Fel vid skapande av betalning: " + e.getMessage());
        }

        return "new-payment";
    }

    // BUG-007: Notification logic embedded directly in the controller.
    // No retry, silently swallows all exceptions.
    // TODO: move to a notification service with proper error handling/retry/queue
    private void sendNotification(Integer attestantId, Integer paymentId, BigDecimal amount) {
        try {
            // Fetch attestant email — yet another DB query inline
            String userSql = "SELECT email, name FROM users WHERE id = " + attestantId;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(userSql);
            if (rows.isEmpty()) {
                return;
            }
            String attestantEmail = (String) rows.get(0).get("email");
            String attestantName = (String) rows.get(0).get("name");

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom("noreply@seb-portal.local");
            msg.setTo(attestantEmail);
            msg.setSubject("Betalning kräver attest — " + amount + " SEK");
            msg.setText("Hej " + attestantName + ",\n\nBetalning #" + paymentId + " på " + amount
                    + " SEK kräver din attest.\n\nLogga in på portalen för att granska.\n\nMvh,\nSEB Företagsbetalningar");

            try {
                mailSender.send(msg);
            } catch (Exception e) {
                // SPAGHETTI: Silently swallows ALL exceptions — failed notifications are invisible
                // No logging, no alerting, no retry queue
            }
        } catch (Exception e) {
            // SPAGHETTI: Swallow silently here too — DB lookup failures are also invisible
        }
    }

    // BUG-008 helper (fully realized in Task 7): append-only file audit log that
    // can diverge from the DB audit_entries table since they're written independently.
    // TODO: single source of truth for audit, not two divergent logs
    private void appendAuditFile(String line) {
        try (FileWriter fw = new FileWriter("/tmp/audit.log", true)) {
            fw.write(line + "\n");
        } catch (IOException e) {
            // SPAGHETTI: Swallow silently — file audit log just stays incomplete
        }
    }

    private void loadAccounts(Object tenantId, Model model) {
        try {
            String sql = "SELECT id, account_name, iban, balance, currency FROM accounts WHERE tenant_id = "
                    + tenantId + " ORDER BY id";
            List<Map<String, Object>> accounts = jdbcTemplate.queryForList(sql);
            model.addAttribute("accounts", accounts);
        } catch (Exception e) {
            // SPAGHETTI: Swallow silently — accounts list just stays empty
            model.addAttribute("accounts", List.of());
        }
    }
}
