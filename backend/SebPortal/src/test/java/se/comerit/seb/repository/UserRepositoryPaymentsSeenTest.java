package se.comerit.seb.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// #230: "sedd"-tidpunkten för initiatorns badge sparas per användare. Testet kör UPDATE- och
// SELECT-frågorna mot en riktig SQL-motor (H2 i minnet, samma upplägg som
// AccountRepositoryTenantTest). Kontrollfallet (rätt användare uppdateras) står först i varje
// test: utan det skulle de negativa fallen kunna vara gröna bara för att inget uppdateras alls.
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryPaymentsSeenTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final LocalDateTime SEEN_AT = LocalDateTime.of(2026, 11, 2, 12, 0, 0);

    @Autowired
    private UserRepository userRepository;

    private User lisa;
    private User otherInTenantA;
    private User userInTenantB;

    @BeforeEach
    void seedUsers() {
        lisa = userRepository.save(new User(TENANT_A, "Lisa", "lisa@a.se", "hash", Role.INITIATOR));
        otherInTenantA = userRepository.save(new User(TENANT_A, "Olle", "olle@a.se", "hash", Role.INITIATOR));
        userInTenantB = userRepository.save(new User(TENANT_B, "Berit", "berit@b.se", "hash", Role.INITIATOR));
    }

    @Test
    void findPaymentsSeenAt_isEmptyForAUserWhoNeverMarkedAnything() {
        // Tomt resultat är det som får PaymentService att räkna alla beslut (NEVER_SEEN)
        assertEquals(Optional.empty(), userRepository.findPaymentsSeenAt(lisa.getId(), TENANT_A));
    }

    @Test
    void markPaymentsSeen_storesTheTimestampForTheUser() {
        int updated = userRepository.markPaymentsSeen(lisa.getId(), TENANT_A, SEEN_AT);

        assertEquals(1, updated);
        assertEquals(Optional.of(SEEN_AT), userRepository.findPaymentsSeenAt(lisa.getId(), TENANT_A));
    }

    @Test
    void markPaymentsSeen_overwritesAnEarlierTimestamp() {
        userRepository.markPaymentsSeen(lisa.getId(), TENANT_A, SEEN_AT);
        userRepository.markPaymentsSeen(lisa.getId(), TENANT_A, SEEN_AT.plusHours(1));

        assertEquals(Optional.of(SEEN_AT.plusHours(1)),
                userRepository.findPaymentsSeenAt(lisa.getId(), TENANT_A));
    }

    @Test
    void markPaymentsSeen_onlyAffectsTheGivenUser() {
        // Kontrollfall: Lisa uppdateras
        assertEquals(1, userRepository.markPaymentsSeen(lisa.getId(), TENANT_A, SEEN_AT));
        assertEquals(Optional.of(SEEN_AT), userRepository.findPaymentsSeenAt(lisa.getId(), TENANT_A));

        // Varken en kollega i samma företag eller en användare i ett annat företag påverkas
        assertTrue(userRepository.findPaymentsSeenAt(otherInTenantA.getId(), TENANT_A).isEmpty());
        assertTrue(userRepository.findPaymentsSeenAt(userInTenantB.getId(), TENANT_B).isEmpty());
    }

    @Test
    void markPaymentsSeen_doesNothingWhenTheTenantDoesNotMatch() {
        // Kontrollfall: rätt tenant uppdaterar
        assertEquals(1, userRepository.markPaymentsSeen(lisa.getId(), TENANT_A, SEEN_AT));

        // Samma användar-id men fel företag uppdaterar ingenting och ändrar inte värdet
        assertEquals(0, userRepository.markPaymentsSeen(lisa.getId(), TENANT_B, SEEN_AT.plusHours(1)));
        assertEquals(Optional.of(SEEN_AT), userRepository.findPaymentsSeenAt(lisa.getId(), TENANT_A));
    }

    @Test
    void findPaymentsSeenAt_isEmptyForAnotherTenantsUser() {
        userRepository.markPaymentsSeen(lisa.getId(), TENANT_A, SEEN_AT);

        // Ett värde som finns, men som inte får läsas av ett annat företag
        assertTrue(userRepository.findPaymentsSeenAt(lisa.getId(), TENANT_B).isEmpty());
    }

    @Test
    void markPaymentsSeen_leavesTheOtherFieldsUntouched() {
        userRepository.markPaymentsSeen(lisa.getId(), TENANT_A, SEEN_AT);

        // UPDATE-frågan rensar persistence-kontexten (clearAutomatically), så detta läses från databasen
        User reloaded = userRepository.findById(lisa.getId()).orElseThrow();
        assertEquals("Lisa", reloaded.getName());
        assertEquals("lisa@a.se", reloaded.getEmail());
        assertEquals(Role.INITIATOR, reloaded.getRole());
        assertEquals(TENANT_A, reloaded.getTenantId());
        assertEquals(SEEN_AT, reloaded.getPaymentsSeenAt());
    }
}