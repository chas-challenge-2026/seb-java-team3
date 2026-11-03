package se.comerit.seb.repository;

import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    List<ApprovalStep> findByPaymentIdOrderByStepNumberAsc(Long paymentId);

    List<ApprovalStep> findByAttestantIdAndStatus(Long attestantId, ApprovalStepStatus status);

    // Har attestanten något steg som väntar? Ett väntande steg hör alltid till en betalning som väntar på attest,
    // eftersom avvisning och godkännande avgör alla steg.
    boolean existsByAttestantIdAndStatus(Long attestantId, ApprovalStepStatus status);

    // Har användaren någon gång fått ett atteststeg tilldelat sig? Används innan en användare tas bort:
    // steget pekar på användaren, och historiken får inte tappa sin attestant.
    boolean existsByAttestantId(Long attestantId);

    // Har användaren fattat ett beslut på något steg? Samma skäl som ovan.
    boolean existsByDecidedBy(Long decidedBy);
}