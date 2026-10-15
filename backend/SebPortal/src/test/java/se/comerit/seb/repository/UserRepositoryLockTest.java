package se.comerit.seb.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// ADR 0012: låset per användare som serialiserar refresh-token-hanteringen. Kör mot H2 i minnet.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryLockTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByIdForUpdate_returnsTheUser_andLocksTheRow() {
        User lisa = users.saveAndFlush(new User(1L, "Lisa Persson", "lisa@test.se", "hash", Role.INITIATOR));
        entityManager.clear();

        User locked = users.findByIdForUpdate(lisa.getId()).orElseThrow();

        assertEquals("lisa@test.se", locked.getEmail());
        // Testet misslyckas om @Lock tas bort: då serialiseras inte längre förnyelser per användare.
        assertEquals(LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(locked));
    }

    @Test
    void findByIdForUpdate_findsNothingForAnUnknownUser() {
        assertTrue(users.findByIdForUpdate(9999L).isEmpty());
    }
}
