package se.comerit.seb.repository;

import se.comerit.seb.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByTenantId(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Payment p
            JOIN p.approvalSteps step
            WHERE step.id = :approvalStepId
            """)
    Optional<Payment> findByApprovalStepIdForUpdate(
            @Param("approvalStepId") Long approvalStepId);
}
