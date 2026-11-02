package se.comerit.seb.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Frågorna som användaradministrationen bygger på (#242): lista ett företags användare och se om en
// e-postadress redan används. Kör mot H2 i minnet, så att frågorna körs på riktigt.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryQueriesTest {

    @Autowired
    private UserRepository users;

    @Test
    void findByTenant_returnsOnlyThatCompanysUsers_sortedByName() {
        users.save(new User(1L, "Cecilia", "cecilia@a.se", "hash", Role.INITIATOR));
        users.save(new User(1L, "Adam", "adam@a.se", "hash", Role.ATTESTANT));
        users.save(new User(2L, "Aaron", "aaron@b.se", "hash", Role.ADMIN));
        users.save(new User(1L, "Bertil", "bertil@a.se", "hash", Role.INITIATOR));
        users.flush();

        List<User> ofCompany1 = users.findByTenantIdOrderByNameAscIdAsc(1L);

        assertEquals(List.of("Adam", "Bertil", "Cecilia"), ofCompany1.stream().map(User::getName).toList());
        assertTrue(ofCompany1.stream().allMatch(user -> user.getTenantId().equals(1L)),
                "Ett annat företags användare får aldrig komma med");
    }

    @Test
    void findByTenant_putsTwoUsersWithTheSameNameInIdOrder() {
        User first = users.save(new User(1L, "Adam", "adam1@a.se", "hash", Role.INITIATOR));
        User second = users.save(new User(1L, "Adam", "adam2@a.se", "hash", Role.ATTESTANT));
        users.flush();

        assertEquals(List.of(first.getId(), second.getId()),
                users.findByTenantIdOrderByNameAscIdAsc(1L).stream().map(User::getId).toList());
    }

    @Test
    void findByTenant_isEmptyForACompanyWithoutUsers() {
        users.saveAndFlush(new User(1L, "Adam", "adam@a.se", "hash", Role.INITIATOR));

        assertTrue(users.findByTenantIdOrderByNameAscIdAsc(99L).isEmpty());
    }

    @Test
    void existsByEmail_isExact_whichIsWhyAddressesAreNormalizedBeforeTheyAreSaved() {
        users.saveAndFlush(new User(1L, "Lisa", "lisa@a.se", "hash", Role.INITIATOR));

        assertTrue(users.existsByEmail("lisa@a.se"));
        assertFalse(users.existsByEmail("Lisa@a.se"));
        assertFalse(users.existsByEmail("nils@a.se"));
    }
}
