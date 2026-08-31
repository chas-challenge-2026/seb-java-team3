package se.comerit.seb.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "approval_steps")
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "attestant_id")
    private Integer attestantId;

    @Column(name = "step_number")
    private Integer stepNumber;

    private String status;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    private String comment;

    public Integer getId() {
        return id;
    }
}
