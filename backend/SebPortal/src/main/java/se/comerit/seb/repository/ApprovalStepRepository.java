package se.comerit.seb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.seb.domain.ApprovalStep;

import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Integer> {
    List<ApprovalStep> findByAttestantIdAndStatus(Integer attestantId, String status);
}
