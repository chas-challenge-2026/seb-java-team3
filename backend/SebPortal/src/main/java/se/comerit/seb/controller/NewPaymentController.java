package se.comerit.seb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
import se.comerit.seb.service.PaymentService;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class NewPaymentController {

    private final PaymentService paymentService;

    public NewPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<?> createPayment(@RequestBody CreatePaymentRequest request,
                                           HttpSession session) {
        Long userId = sessionLong(session, "userId");
        Long tenantId = sessionLong(session, "tenantId");

        if (userId == null || tenantId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not logged in"));
        }

        CreatePaymentRequest authenticatedRequest = new CreatePaymentRequest(
                tenantId,
                request.fromAccountId(),
                request.toIban(),
                request.amount(),
                request.reference(),
                userId
        );

        PaymentResponse response = paymentService.createPayment(authenticatedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Long sessionLong(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        return value instanceof Number number ? number.longValue() : null;
    }
}
