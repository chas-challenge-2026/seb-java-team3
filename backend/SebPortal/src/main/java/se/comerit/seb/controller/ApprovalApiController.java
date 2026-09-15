package se.comerit.seb.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
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
import se.comerit.seb.service.ApprovalService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalApiController {

    private final PaymentRepository paymentRepository;
    private final ApprovalService approvalService;

    public ApprovalApiController(PaymentRepository paymentRepository,
                                 ApprovalService approvalService) {
        this.paymentRepository = paymentRepository;
        this.approvalService = approvalService;
    }

    @GetMapping
    public ResponseEntity<?> pendingApprovals(HttpSession session) {
        Long userId = sessionLong(session, "userId");
        Long tenantId = sessionLong(session, "tenantId");

        if (userId == null || tenantId == null) {
            return notLoggedIn();
        }

        List<PendingApprovalResponse> approvals = paymentRepository
                .findPendingApprovalsForAttestant(tenantId, userId)
                .stream()
                .flatMap(payment -> pendingStepsForUser(payment, userId).stream())
                .toList();

        return ResponseEntity.ok(approvals);
    }

    @PostMapping("/{stepId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long stepId, HttpSession session) {
        Long userId = sessionLong(session, "userId");

        if (userId == null) {
            return notLoggedIn();
        }

        approvalService.approve(stepId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{stepId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long stepId,
                                    @RequestBody(required = false) RejectApprovalRequest request,
                                    HttpSession session) {
        Long userId = sessionLong(session, "userId");

        if (userId == null) {
            return notLoggedIn();
        }

        String comment = request == null ? null : request.comment();
        approvalService.reject(stepId, userId, comment);
        return ResponseEntity.noContent().build();
    }

    private List<PendingApprovalResponse> pendingStepsForUser(Payment payment, Long userId) {
        return payment.getApprovalSteps().stream()
                .filter(step -> step.getStatus() == ApprovalStepStatus.PENDING)
                .filter(step -> userId.equals(step.getAttestantId()))
                .map(step -> PendingApprovalResponse.from(payment, step))
                .toList();
    }

    private ResponseEntity<Map<String, String>> notLoggedIn() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Not logged in"));
    }

    private Long sessionLong(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        return value instanceof Number number ? number.longValue() : null;
    }

    public record RejectApprovalRequest(String comment) {}
}
