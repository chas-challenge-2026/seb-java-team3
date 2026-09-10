package se.comerit.seb.repository;

import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByTenantIdAndRole(Long tenantId, Role role);
}