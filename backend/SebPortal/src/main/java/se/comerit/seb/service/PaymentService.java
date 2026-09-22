package se.comerit.seb.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.config.ApprovalThresholds;
import se.comerit.seb.domain.*;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
import se.comerit.seb.infrastructure.iban.IbanValidatorService;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ApprovalThresholds thresholds;
    private final AuditService auditService;
    private final IbanValidatorService ibanValidator;

    // Constructor injection: Spring skapar automatiskt en instans av
    // PaymentService och fyller i dessa tre beroenden åt dig, baserat
    // på att de redan är @Service/@Component/@Repository någon annanstans.
    public PaymentService(PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          ApprovalThresholds thresholds,
                          AuditService auditService,
                          IbanValidatorService ibanValidator) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.thresholds = thresholds;
        this.auditService = auditService;
        this.ibanValidator = ibanValidator;
    }

    private String normalizeAndValidateIban(CreatePaymentRequest request) {
        if (request.toIban() == null || request.toIban().isBlank()) {
            throw new IllegalArgumentException("IBAN får inte vara tomt");
        }

        String normalizedIban = ibanValidator.normalize(request.toIban());

        if (!ibanValidator.validateIban(normalizedIban)) {
            throw new IllegalArgumentException(
                    "Invalid IBAN: " + ibanValidator.getIbanErrorMessage(normalizedIban)
            );
        }

        return normalizedIban;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Beloppet måste vara större än noll");
        }
    }

    private User findAttestant(Long tenantId) {
        List<User> attestants = userRepository.findByTenantIdAndRole(tenantId, Role.ATTESTANT);

        if (attestants.isEmpty()) {
            throw new NoAttestantAvailableException(tenantId);
        }

        // MVP-regel: ta den första hittade attestanten.
        // Dokumenterad avgränsning - riktig urvalslogik (t.ex.
        // arbetsbelastning, roterande tilldelning) är inte MVP.
        return attestants.get(0);
    }

    @PreAuthorize("hasAnyRole('INITIATOR', 'ADMIN')")
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {

        String normalizedIban = normalizeAndValidateIban(request);
        validateAmount(request.amount());

        Payment payment = new Payment(
                request.tenantId(),
                request.fromAccountId(),
                normalizedIban,
                request.amount(),
                request.reference(),
                request.createdBy()
        );

        if (request.amount().compareTo(thresholds.getNoAttestantThreshold()) <= 0) {
            // Upp till och med tröskeln (strikt > krävs för attest): ingen attestant
            // behövs, betalningen är klar direkt
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExecutedAt(LocalDateTime.now());

        } else {
            // Över tröskeln: skapa minst ett godkännandesteg.
            // OBS: belopp över two-attestant-threshold hanteras just nu likadant som 1-attestant-
            // fallet - riktig 2-attestant-kedja är avgränsad från #43.
            User attestant = findAttestant(request.tenantId());

            ApprovalStep step = new ApprovalStep(attestant.getId(), 1);
            payment.addApprovalStep(step);
            // status är redan PENDING_APPROVAL som default i Payment
        }

        Payment saved = paymentRepository.save(payment);

        String description = "Betalning skapad: %s %s till %s".formatted(
                saved.getAmount(), saved.getCurrency(), saved.getToIban());

        auditService.record(
                saved.getTenantId(),
                saved.getCreatedBy(),
                "CREATE_PAYMENT",
                "PAYMENT",
                saved.getId(),
                description
        );

        return PaymentResponse.from(saved);
    }

}
