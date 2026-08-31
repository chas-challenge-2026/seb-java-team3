package se.comerit.seb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.comerit.seb.domain.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
}
