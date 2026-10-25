package se.comerit.seb.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.comerit.seb.dto.PendingApprovalResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.ApprovalService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalApiController {

    private final ApprovalService approvalService;
    private final JwtUserContext jwtUserContext;

    public ApprovalApiController(ApprovalService approvalService,
                                 JwtUserContext jwtUserContext) {
        this.approvalService = approvalService;
        this.jwtUserContext = jwtUserContext;
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @GetMapping
    public ResponseEntity<?> pendingApprovals() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();

        List<PendingApprovalResponse> approvals = approvalService.getPendingApprovals(user);
        return ResponseEntity.ok(approvals);
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @GetMapping("/count")
    public ResponseEntity<Map<String, Integer>> pendingApprovalCount() {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();
        int count = approvalService.countPendingByAttestant(user);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @PostMapping("/{stepId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long stepId) {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();

        approvalService.approveForUser(stepId, user);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ATTESTANT', 'ADMIN')")
    @PostMapping("/{stepId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long stepId,
                                    @RequestBody(required = false) RejectApprovalRequest request) {
        AuthenticatedUserContext user = jwtUserContext.requireAuthenticated();

        String comment = request == null ? null : request.comment();
        approvalService.rejectForUser(stepId, user, comment);
        return ResponseEntity.noContent().build();
    }

    public record RejectApprovalRequest(String comment) {}
}