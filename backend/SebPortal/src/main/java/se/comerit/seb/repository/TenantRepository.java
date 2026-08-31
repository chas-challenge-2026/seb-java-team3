package se.comerit.seb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.seb.domain.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, Integer> {
}
