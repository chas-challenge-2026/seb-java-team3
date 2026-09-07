package se.comerit.seb.repository;

import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {


    List<User> findByTenantIdAndRole(Long tenantId, Role role);
}