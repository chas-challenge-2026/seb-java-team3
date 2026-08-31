package se.comerit.seb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.seb.domain.Payment;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByTenantIdOrderByCreatedAtDesc(Integer tenantId);
}
