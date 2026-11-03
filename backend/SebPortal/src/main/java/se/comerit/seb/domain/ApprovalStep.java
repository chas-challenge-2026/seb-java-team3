package se.comerit.seb.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_steps")
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "attestant_id")
    private Long attestantId;

    @Column(name = "step_number")
    private Integer stepNumber = 1;

    @Convert(converter = ApprovalStepStatusConverter.class)
    @Column(name = "status", length = 20)
    private ApprovalStepStatus status = ApprovalStepStatus.PENDING;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    // Den som fattade beslutet, som kan vara en admin i stället för den tilldelade attestanten. Tom på
    // steg från före V12 och på steg som stängdes automatiskt när ett tidigare steg avvisades.
    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "comment", length = 255)
    private String comment;

    protected ApprovalStep() {
        // Krävs av JPA/Hibernate
    }

    public ApprovalStep(Long attestantId, Integer stepNumber) {
        this.attestantId = attestantId;
        this.stepNumber = stepNumber;
    }

    public Long getId() { return id; }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }

    public Long getAttestantId() { return attestantId; }
    public void setAttestantId(Long attestantId) { this.attestantId = attestantId; }

    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }

    public ApprovalStepStatus getStatus() { return status; }
    public void setStatus(ApprovalStepStatus status) { this.status = status; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}