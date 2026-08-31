package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import se.comerit.seb.repository.PaymentRepository;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }
}
