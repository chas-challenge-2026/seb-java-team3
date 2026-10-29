package se.comerit.seb.repository;

import se.comerit.seb.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByTenantId(Long tenantId);

    // Reserverat belopp på ett konto: summan av betalningar från kontot som väntar på attest. Tillgängligt
    // saldo är saldot minus det, så en väntande betalning håller sina pengar tills den godkänns (dras)
    // eller avvisas (släpps). Inget lagras, så reservationen kan aldrig komma ur synk med betalningarna.
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.tenantId = :tenantId
              AND p.fromAccountId = :accountId
              AND p.status = se.comerit.seb.domain.PaymentStatus.PENDING_APPROVAL
            """)
    BigDecimal sumPendingAmountForAccount(
            @Param("tenantId") Long tenantId,
            @Param("accountId") Long accountId);

    interface ReservedAmount {
        Long getAccountId();

        BigDecimal getTotal();
    }

    // Samma reservation för alla företagets konton på en gång (kontolistan).
    @Query("""
            SELECT p.fromAccountId AS accountId, SUM(p.amount) AS total
            FROM Payment p
            WHERE p.tenantId = :tenantId
              AND p.status = se.comerit.seb.domain.PaymentStatus.PENDING_APPROVAL
            GROUP BY p.fromAccountId
            """)
    List<ReservedAmount> sumPendingAmountPerAccount(@Param("tenantId") Long tenantId);

    // Auditloggen: betalningarna bakom en lista händelser, med atteststegen i samma fråga och bara inom företaget.
    @Query("""
            SELECT DISTINCT p
            FROM Payment p
            LEFT JOIN FETCH p.approvalSteps
            WHERE p.tenantId = :tenantId
              AND p.id IN :paymentIds
            """)
    List<Payment> findByTenantIdAndIdInWithApprovalSteps(
            @Param("tenantId") Long tenantId,
            @Param("paymentIds") List<Long> paymentIds);

    // Mina betalningar: atteststegen hämtas i samma fråga, så att listan inte ger en extra fråga per betalning.
    @Query("""
            SELECT DISTINCT p
            FROM Payment p
            LEFT JOIN FETCH p.approvalSteps
            WHERE p.tenantId = :tenantId
              AND p.createdBy = :createdBy
            ORDER BY p.createdAt DESC
            """)
    List<Payment> findByTenantIdAndCreatedByWithApprovalSteps(
            @Param("tenantId") Long tenantId,
            @Param("createdBy") Long createdBy);

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

    @Query("""
            SELECT DISTINCT p
            FROM Payment p
            JOIN FETCH p.approvalSteps step
            WHERE p.tenantId = :tenantId
              AND p.status = se.comerit.seb.domain.PaymentStatus.PENDING_APPROVAL
              AND step.status = se.comerit.seb.domain.ApprovalStepStatus.PENDING
            ORDER BY p.createdAt ASC
            """)
    List<Payment> findPendingApprovalsForTenant(@Param("tenantId") Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Payment p
            JOIN p.approvalSteps step
            WHERE step.id = :approvalStepId
            """)
    Optional<Payment> findByApprovalStepIdForUpdate(
            @Param("approvalStepId") Long approvalStepId);

    // Initiatorns betalningar där en attestant har fattat beslut (godkänt eller avvisat). Betalningar
    // under tröskeln blir COMPLETED direkt utan atteststeg och räknas därför inte som "ändrade".
    @Query("""
            SELECT COUNT(p)
            FROM Payment p
            WHERE p.tenantId = :tenantId
              AND p.createdBy = :createdBy
              AND p.status IN (se.comerit.seb.domain.PaymentStatus.COMPLETED,
                               se.comerit.seb.domain.PaymentStatus.REJECTED)
              AND p.approvalSteps IS NOT EMPTY
            """)
    long countDecidedByInitiator(
            @Param("tenantId") Long tenantId,
            @Param("createdBy") Long createdBy);
}
