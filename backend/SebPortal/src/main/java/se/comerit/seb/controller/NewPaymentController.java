package se.comerit.seb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
import se.comerit.seb.config.ApprovalThresholds;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.CsvPaymentImportService;
import se.comerit.seb.service.PaymentService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class NewPaymentController {

    private final PaymentService paymentService;
    private final CsvPaymentImportService csvPaymentImportService;
    private final JwtUserContext jwtUserContext;
    private final ApprovalThresholds approvalThresholds;

    public NewPaymentController(PaymentService paymentService,
                                CsvPaymentImportService csvPaymentImportService,
                                JwtUserContext jwtUserContext,
                                ApprovalThresholds approvalThresholds) {
        this.paymentService = paymentService;
        this.csvPaymentImportService = csvPaymentImportService;
        this.jwtUserContext = jwtUserContext;
        this.approvalThresholds = approvalThresholds;
    }

    public record PaymentConfigResponse(BigDecimal approvalThreshold, BigDecimal twoAttestantThreshold) {}

    // Bara INITIATOR skapar betalningar. ADMIN är en administrativ roll och flyttar inga pengar (ADR 0017).
    @PreAuthorize("hasRole('INITIATOR')")
    @GetMapping("/config")
    public PaymentConfigResponse getPaymentConfig() {
        return new PaymentConfigResponse(
                approvalThresholds.getNoAttestantThreshold(),
                approvalThresholds.getTwoAttestantThreshold());
    }

    @PreAuthorize("hasRole('INITIATOR')")
    @PostMapping
    public ResponseEntity<?> createPayment(@RequestBody CreatePaymentRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey) {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();

        CreatePaymentRequest authenticatedRequest = new CreatePaymentRequest(
                user.tenantId(),
                request.fromAccountId(),
                request.toIban(),
                request.amount(),
                request.reference(),
                user.userId()
        );

        PaymentResponse response = idempotencyKey == null
                ? paymentService.createPayment(authenticatedRequest)
                : paymentService.createPayment(authenticatedRequest, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('INITIATOR')")
    @GetMapping("/changed-count")
    public ResponseEntity<Map<String, Integer>> changedCount() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        return ResponseEntity.ok(Map.of("count", paymentService.countChangedForInitiator(user)));
    }

    @PreAuthorize("hasRole('INITIATOR')")
    @PostMapping("/changed-count/seen")
    public ResponseEntity<Void> changedSeen() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        paymentService.markChangedSeen(user);
        return ResponseEntity.noContent().build();
    }

    // Samma regel som createPayment: bara INITIATOR skapar betalningar (ADR 0017).
    @PreAuthorize("hasRole('INITIATOR')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<PaymentResponse>> importPayments(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Välj en CSV-fil att ladda upp.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Filen måste ha filändelsen .csv.");
        }
        if (file.getSize() > CsvPaymentImportService.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("CSV-filen får vara högst 1 MB.");
        }

        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        try {
            List<PaymentResponse> response = csvPaymentImportService.importCsv(
                    file.getBytes(), user.tenantId(), user.userId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException ex) {
            throw new UncheckedIOException("Kunde inte läsa den uppladdade CSV-filen.", ex);
        }
    }
}

