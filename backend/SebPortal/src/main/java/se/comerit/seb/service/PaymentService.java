package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.config.ApprovalThresholds;
import se.comerit.seb.domain.*;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
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

    // Constructor injection: Spring skapar automatiskt en instans av
    // PaymentService och fyller i dessa tre beroenden åt dig, baserat
    // på att de redan är @Service/@Component/@Repository någon annanstans.
    public PaymentService(PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          ApprovalThresholds thresholds) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.thresholds = thresholds;
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

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {

        validateAmount(request.amount());

        Payment payment = new Payment(
                request.tenantId(),
                request.fromAccountId(),
                request.toIban(),
                request.amount(),
                request.reference(),
                request.createdBy()
        );

        if (request.amount().compareTo(thresholds.getNoAttestantThreshold()) < 0) {
            // Under 5000: ingen attestant behövs, betalningen är klar direkt
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExecutedAt(LocalDateTime.now());

        } else {
            // 5000 eller mer: skapa minst ett godkännandesteg.
            // OBS: >=10000 hanteras just nu likadant som 1-attestant-
            // fallet - riktig 2-attestant-kedja är avgränsad från #43.
            User attestant = findAttestant(request.tenantId());

            ApprovalStep step = new ApprovalStep(attestant.getId(), 1);
            payment.addApprovalStep(step);
            // status är redan PENDING_APPROVAL som default i Payment
        }

        Payment saved = paymentRepository.save(payment);
        return PaymentResponse.from(saved);
    }

}