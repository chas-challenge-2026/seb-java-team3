package se.comerit.seb.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.dto.PendingApprovalResponse;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.ApprovalService;

import java.util.List;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalApiController {

    private final PaymentRepository paymentRepository;
    private final ApprovalService approvalService;
    private final JwtUserContext jwtUserContext;

    public ApprovalApiController(PaymentRepository paymentRepository,
                                 ApprovalService approvalService,
                                 JwtUserContext jwtUserContext) {
        this.paymentRepository = paymentRepository;
        this.approvalService = approvalService;
        this.jwtUserContext = jwtUserContext;
    }

    @GetMapping
    public ResponseEntity<?> pendingApprovals() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();

        List<PendingApprovalResponse> approvals = paymentRepository
                .findPendingApprovalsForAttestant(user.tenantId(), user.userId())
                .stream()
                .flatMap(payment -> pendingStepsForUser(payment, user.userId()).stream())
                .toList();

        return ResponseEntity.ok(approvals);
    }

    @GetMapping("/count")
public ResponseEntity<Map<String, Integer>> pendingApprovalCount() {
    AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
    int count = approvalService.countPendingByAttestant(user);
    return ResponseEntity.ok(Map.of("count", count));
}

    @PostMapping("/{stepId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long stepId) {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();

        approvalService.approve(stepId, user.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{stepId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long stepId,
                                    @RequestBody(required = false) RejectApprovalRequest request) {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();

        String comment = request == null ? null : request.comment();
        approvalService.reject(stepId, user.userId(), comment);
        return ResponseEntity.noContent().build();
    }

    private List<PendingApprovalResponse> pendingStepsForUser(Payment payment, Long userId) {
        return payment.getApprovalSteps().stream()
                .filter(step -> step.getStatus() == ApprovalStepStatus.PENDING)
                .filter(step -> userId.equals(step.getAttestantId()))
                .map(step -> PendingApprovalResponse.from(payment, step))
                .toList();
    }

    public record RejectApprovalRequest(String comment) {}
}