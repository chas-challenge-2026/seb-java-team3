package se.comerit.seb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import se.comerit.seb.config.PaymentThresholdProperties;

import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// SPAGHETTI: ported straight from BatchUpload.cshtml.cs — all parsing, validation and
// persistence logic crammed into the controller, no service layer.
@Controller
public class BatchController {

    // TODO: this should be in a service/repository layer, but inline works for now
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentThresholdProperties paymentThresholds;

    // BUG-003: same weak IBAN regex as PaymentController — copy-pasted, not shared, no MOD97
    // TODO: implement proper MOD97 IBAN checksum validation
    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

    @GetMapping("/batch")
    public String uploadForm(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "batch-upload";
    }

    @PostMapping("/batch")
    public String uploadBatch(@RequestParam("file") MultipartFile file,
                               HttpSession session,
                               Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        Object userId = session.getAttribute("userId");
        Object tenantId = session.getAttribute("tenantId");

        if (file == null || file.isEmpty()) {
            model.addAttribute("resultMessage", "Ingen fil vald.");
            model.addAttribute("hasErrors", true);
            return "batch-upload";
        }

        // BUG-012: UI says "Max 1 MB" but there is no actual size check here — the multipart
        // limit is already disabled in application.properties (max-file-size=-1). A 100MB file
        // would be accepted and fully read into memory below.
        // TODO: enforce a real size limit (and check it BEFORE reading the whole stream)
        String csvContent;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            // SPAGHETTI: reads the entire file into memory in one go, no streaming/chunking
            int c;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
            csvContent = sb.toString();
        } catch (IOException e) {
            model.addAttribute("resultMessage", "Kunde inte läsa filen: " + e.getMessage());
            model.addAttribute("hasErrors", true);
            return "batch-upload";
        }

        // SPAGHETTI: naive line splitting, no proper CSV/line-ending handling
        String[] lines = csvContent.split("\n");
        List<String> nonEmptyLines = new ArrayList<>();
        for (String l : lines) {
            if (!l.trim().isEmpty()) {
                nonEmptyLines.add(l);
            }
        }

        if (nonEmptyLines.size() < 2) {
            model.addAttribute("resultMessage", "CSV-filen är tom eller saknar datarader.");
            model.addAttribute("hasErrors", true);
            return "batch-upload";
        }

        // SPAGHETTI: No validation of header row — just skip it and assume columns are correct
        List<String> dataLines = nonEmptyLines.subList(1, nonEmptyLines.size());

        int processed = 0;
        int failed = 0;
        int rowNumber = 1;
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> processedRows = new ArrayList<>();

        // BUG-005: No DB transaction — inserts happen one by one via separate jdbcTemplate.update
        // calls. Partial success is possible: rows 1-2 succeed, row 3 fails, rows 4+ still succeed
        // and get committed individually. No @Transactional anywhere in this method.
        // TODO: wrap the whole batch in a single transaction so it's all-or-nothing
        for (String line : dataLines) {
            rowNumber++;
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            // BUG-004: CSV parsing with String.split(",") directly — no quoted-field handling,
            // no escaping, no trimming of individual fields. A reference like
            // "Malmö Bygg, projektfaktura" breaks this: the comma inside the field is treated
            // as a column separator, so parts.length grows and the wrong value lands in
            // each column (or it's rejected as "too few/many columns").
            // TODO: use a real CSV parser (e.g. a library) that honors quoted fields
            String[] parts = trimmedLine.split(",");

            java.util.LinkedHashMap<String, Object> rowResult = new java.util.LinkedHashMap<>();
            rowResult.put("rowNumber", rowNumber);

            if (parts.length < 4) {
                String errMsg = "Rad " + rowNumber + ": För få kolumner (förväntade 4, fick " + parts.length + ")";
                errors.add(errMsg);
                rowResult.put("error", errMsg);
                rowResult.put("success", false);
                processedRows.add(rowResult);
                failed++;
                continue;
            }

            // SPAGHETTI: No trimming of individual fields — leading/trailing spaces cause
            // parse failures (e.g. " 1" as account id, " SE85..." as IBAN)
            String fromAccountIdStr = parts[0];
            String toIban = parts[1];
            String amountStr = parts[2];
            // SPAGHETTI: Reference is assumed to be parts[3] — but "Faktura, #1234" breaks this.
            // If there are more parts (because reference had a comma), they're silently discarded.
            String reference = parts[3];

            rowResult.put("toIban", toIban);
            rowResult.put("reference", reference);

            Integer fromAccountId;
            try {
                fromAccountId = Integer.parseInt(fromAccountIdStr.trim());
            } catch (NumberFormatException e) {
                String errMsg = "Rad " + rowNumber + ": Ogiltigt konto-ID '" + fromAccountIdStr + "'";
                errors.add(errMsg);
                rowResult.put("error", errMsg);
                rowResult.put("success", false);
                processedRows.add(rowResult);
                failed++;
                continue;
            }

            BigDecimal amount;
            try {
                amount = new BigDecimal(amountStr.trim());
            } catch (NumberFormatException e) {
                String errMsg = "Rad " + rowNumber + ": Ogiltigt belopp '" + amountStr + "'";
                errors.add(errMsg);
                rowResult.put("error", errMsg);
                rowResult.put("success", false);
                processedRows.add(rowResult);
                failed++;
                continue;
            }

            rowResult.put("amount", amount);

            // BUG-003: same weak IBAN regex as PaymentController, copy-pasted, no MOD97
            String ibanStripped = toIban.replace(" ", "").trim();
            if (!IBAN_PATTERN.matcher(ibanStripped).matches()) {
                String errMsg = "Rad " + rowNumber + ": Ogiltigt IBAN '" + toIban + "'";
                errors.add(errMsg);
                rowResult.put("error", errMsg);
                rowResult.put("success", false);
                processedRows.add(rowResult);
                failed++;
                continue;
            }

            // SPAGHETTI: No ownership check on fromAccountId — any account ID can be used,
            // only the tenant_id stamped on the row comes from the session.
            try {
                String status = amount.compareTo(paymentThresholds.getApprovalThreshold()) > 0 ? "pending_approval" : "completed";

                String escapedReference = reference.trim().replace("'", "''");
                String insertSql = "INSERT INTO payments (tenant_id, from_account_id, to_iban, amount, currency, reference, status, created_by, created_at) "
                        + "VALUES (" + tenantId + ", " + fromAccountId + ", '" + ibanStripped + "', " + amount
                        + ", 'SEK', '" + escapedReference + "', '" + status + "', " + userId + ", NOW()) RETURNING id";
                // BUG-005: each row is its own jdbcTemplate call/commit — no overarching transaction
                Integer paymentId = jdbcTemplate.queryForObject(insertSql, Integer.class);

                // Audit: BATCH_PAYMENT goes to FILE ONLY (BUG-008) — never appears in the UI
                // AuditLog page, which only reads from audit_entries in the DB.
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                appendAuditFile(timestamp + " | BATCH_PAYMENT | user=" + userId + " | payment=" + paymentId
                        + " | amount=" + amount + " | to=" + ibanStripped);

                // SPAGHETTI: If approval needed, create step but DO NOT notify the attestant —
                // mirrors PaymentController's notify path being skipped here entirely.
                if ("pending_approval".equals(status)) {
                    String attestantSql = "SELECT id FROM users WHERE tenant_id = " + tenantId + " AND role = 'attestant' LIMIT 1";
                    List<Map<String, Object>> attestantRows = jdbcTemplate.queryForList(attestantSql);
                    Integer attestantId = attestantRows.isEmpty() ? 0 : (Integer) attestantRows.get(0).get("id");

                    if (attestantId > 0) {
                        String stepSql = "INSERT INTO approval_steps (payment_id, attestant_id, step_number, status) "
                                + "VALUES (" + paymentId + ", " + attestantId + ", 1, 'pending')";
                        jdbcTemplate.update(stepSql);
                    }
                }

                rowResult.put("success", true);
                processedRows.add(rowResult);
                processed++;
            } catch (Exception e) {
                // SPAGHETTI: Row-level exception, no rollback of already-inserted rows (BUG-005)
                String errMsg = "Rad " + rowNumber + ": Databasfel — " + e.getMessage();
                errors.add(errMsg);
                rowResult.put("error", errMsg);
                rowResult.put("success", false);
                processedRows.add(rowResult);
                failed++;
            }
        }

        boolean hasErrors = failed > 0;
        // BUG-005: Reports "X av Y behandlade" even on partial success — the user might think
        // everything succeeded when half the rows actually failed and were never rolled back.
        String resultMessage = processed + " av " + (processed + failed) + " betalningar behandlade.";
        if (failed > 0) {
            resultMessage += " " + failed + " rad(er) misslyckades — se fellistning nedan.";
        }

        model.addAttribute("resultMessage", resultMessage);
        model.addAttribute("hasErrors", hasErrors);
        model.addAttribute("errors", errors);
        model.addAttribute("processedRows", processedRows);

        return "batch-upload";
    }

    // BUG-008: append-only file audit log that can diverge from the DB audit_entries table
    // since they're written independently. BATCH_PAYMENT entries ONLY ever land here.
    // TODO: single source of truth for audit, not two divergent logs
    private void appendAuditFile(String line) {
        try (FileWriter fw = new FileWriter("/tmp/audit.log", true)) {
            fw.write(line + "\n");
        } catch (IOException e) {
            // SPAGHETTI: Swallow silently — file audit log just stays incomplete
        }
    }
}
