package se.comerit.seb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.seb.domain.Account;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    List<Account> findByTenantIdOrderById(Integer tenantId);
}
