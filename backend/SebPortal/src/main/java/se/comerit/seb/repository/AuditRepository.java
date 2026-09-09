package se.comerit.seb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.seb.domain.AuditEntry;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {
}