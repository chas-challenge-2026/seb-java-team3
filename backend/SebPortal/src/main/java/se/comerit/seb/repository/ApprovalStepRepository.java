package se.comerit.seb.repository;

import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    List<ApprovalStep> findByPaymentIdOrderByStepNumberAsc(Long paymentId);

    List<ApprovalStep> findByAttestantIdAndStatus(Long attestantId, ApprovalStepStatus status);
}