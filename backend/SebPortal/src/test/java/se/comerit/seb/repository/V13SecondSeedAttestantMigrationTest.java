package se.comerit.seb.repository;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// ADR 0016: seed-företaget behöver två attestanter, annars går det inte att skapa en betalning över 50 000 kr
// i dev och i demon. Johan ska stå först, så att betalningar med ett steg går till samma attestant som tidigare.
class V13SecondSeedAttestantMigrationTest {

    @Test
    void seedCompanyHasTwoAttestants_withJohanFirst_andTheSameLoginAsTheOtherSeedUsers() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v13-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("13")
                .load()
                .migrate();

        List<String> attestants = jdbc.queryForList(
                "SELECT email FROM users WHERE tenant_id = 1 AND role = 'attestant' ORDER BY id", String.class);
        assertEquals(List.of("johan@malmobygg.se", "eva@malmobygg.se"), attestants);

        String evasHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'eva@malmobygg.se'", String.class);
        String johansHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'johan@malmobygg.se'", String.class);
        assertEquals(johansHash, evasHash);
        assertTrue(evasHash.startsWith("$2"), "lösenordet ska vara bcrypt-hashat");
    }
}
