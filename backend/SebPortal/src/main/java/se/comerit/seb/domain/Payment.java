package se.comerit.seb.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "from_account_id")
    private Long fromAccountId;

    @Column(name = "to_iban", length = 34)
    private String toIban;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency = "SEK";

    @Column(name = "reference", length = 100)
    private String reference;

    @Convert(converter = PaymentStatusConverter.class)
    @Column(name = "status", length = 30)
    private PaymentStatus status = PaymentStatus.PENDING_APPROVAL;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    private List<ApprovalStep> approvalSteps = new ArrayList<>();



    protected Payment() {
        // Krävs av JPA/Hibernate - används aldrig direkt i din egen kod
    }

    public Payment(Long tenantId, Long fromAccountId, String toIban, BigDecimal amount,
                   String reference, Long createdBy) {
        this.tenantId = tenantId;
        this.fromAccountId = fromAccountId;
        this.toIban = toIban;
        this.amount = amount;
        this.reference = reference;
        this.createdBy = createdBy;
    }

    public void addApprovalStep(ApprovalStep step) {
        approvalSteps.add(step);
        step.setPayment(this);
    }

    public Long getId() { return id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }

    public String getToIban() { return toIban; }
    public void setToIban(String toIban) { this.toIban = toIban; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public List<ApprovalStep> getApprovalSteps() { return approvalSteps; }

}