package se.comerit.seb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.PaymentService;

@RestController
@RequestMapping("/api/payments")
public class NewPaymentController {

    private final PaymentService paymentService;
    private final JwtUserContext jwtUserContext;

    public NewPaymentController(PaymentService paymentService, JwtUserContext jwtUserContext) {
        this.paymentService = paymentService;
        this.jwtUserContext = jwtUserContext;
    }

    @PreAuthorize("hasAnyRole('INITIATOR', 'ADMIN')")
    @PostMapping
    public ResponseEntity<?> createPayment(@RequestBody CreatePaymentRequest request) {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();

        CreatePaymentRequest authenticatedRequest = new CreatePaymentRequest(
                user.tenantId(),
                request.fromAccountId(),
                request.toIban(),
                request.amount(),
                request.reference(),
                user.userId()
        );

        PaymentResponse response = paymentService.createPayment(authenticatedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
