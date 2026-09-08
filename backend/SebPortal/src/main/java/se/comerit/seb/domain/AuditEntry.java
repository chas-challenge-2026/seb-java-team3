package se.comerit.seb.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditEntry() {
        // Krävs av JPA/Hibernate
    }

    public AuditEntry(Long tenantId, Long userId, String action,
                      String entityType, Long entityId, String description) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getUserId() { return userId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}