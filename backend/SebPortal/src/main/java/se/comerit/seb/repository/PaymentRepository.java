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

    List<Payment> findByTenantIdAndCreatedByOrderByCreatedAtDesc(Long tenantId, Long createdBy);

    @Query("""
            SELECT payment
            FROM Payment payment
            LEFT JOIN FETCH payment.approvalSteps
            WHERE payment.id = :paymentId
              AND payment.tenantId = :tenantId
            """)
    Optional<Payment> findByIdAndTenantIdWithApprovalSteps(
            @Param("paymentId") Long paymentId,
            @Param("tenantId") Long tenantId);

    @Query("""
            SELECT DISTINCT p
            FROM Payment p
            JOIN FETCH p.approvalSteps step
            WHERE p.tenantId = :tenantId
              AND p.status = se.comerit.seb.domain.PaymentStatus.PENDING_APPROVAL
              AND step.attestantId = :attestantId
              AND step.status = se.comerit.seb.domain.ApprovalStepStatus.PENDING
            ORDER BY p.createdAt ASC
            """)
    List<Payment> findPendingApprovalsForAttestant(
            @Param("tenantId") Long tenantId,
            @Param("attestantId") Long attestantId);

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
