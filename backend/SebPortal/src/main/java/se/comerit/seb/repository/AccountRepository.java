package se.comerit.seb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.seb.domain.Account;

public interface AccountRepository extends JpaRepository<Account, Integer> {

    // Spring Data bygger frågan från metodnamnet:
    // SELECT count(*) > 0 FROM accounts WHERE id = ? AND tenant_id = ?
    // Parametriserad, alltså ingen risk för SQL-injektion.
    boolean existsByIdAndTenantId(Integer id, Long tenantId);
}