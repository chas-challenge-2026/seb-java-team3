package se.comerit.seb.repository;

import jakarta.persistence.LockModeType;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByTenantIdAndRole(Long tenantId, Role role);

    // Låser användarens rad (SELECT ... FOR UPDATE) tills transaktionen är klar. Används som ett
    // lås per användare: RefreshTokenService tar det först i inloggning, förnyelse och utloggning,
    // så att två sådana operationer för samma användare aldrig överlappar (ADR 0012). Utan det kan
    // en spärr av en token-familj missa en efterföljare som skapas samtidigt.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM User u
            WHERE u.id = :userId
            """)
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
