package se.comerit.seb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.seb.domain.Account;

public interface AccountRepository extends JpaRepository<Account, Integer> {
}
