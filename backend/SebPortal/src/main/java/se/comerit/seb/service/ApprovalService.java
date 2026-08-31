package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import se.comerit.seb.repository.ApprovalStepRepository;

@Service
public class ApprovalService {

    private final ApprovalStepRepository approvalStepRepository;

    public ApprovalService(ApprovalStepRepository approvalStepRepository) {
        this.approvalStepRepository = approvalStepRepository;
    }

    @PreAuthorize("hasRole('attestant')")
    public void approve(Integer paymentId, Integer approvalStepId, Integer actorId) {
        // Scaffold for v2 approval flow. Existing v1 controller still owns behavior.
    }
}
