package se.comerit.seb.repository;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// BUG-002: kör de riktiga Flyway-migreringarna V1–V7 mot H2 i PostgreSQL-läge och kontrollerar
// att ingen MD5-hash finns kvar efteråt. Övriga databastester skapar schemat från entiteterna
// (ddl-auto) och kör aldrig migreringarna, så utan det här testet är V7 overifierad.
// OBS: V7 byter bara ut seedens hash för "password123". En användare med annat lösenord får
// ingen ny hash av V7, men nekas ändå vid inloggning (AuthServiceMd5Test).
class V7PasswordMigrationTest {

    private static final Pattern MD5 = Pattern.compile("^[0-9a-f]{32}$");
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v7-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("7")
                .load()
                .migrate();

        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void seedUsersExistAfterMigration() {
        // Annars skulle testerna nedan vara gröna för en tom tabell
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
    }

    @Test
    void noPasswordHashLooksLikeMd5() {
        List<String> md5Left = hashes().stream()
                .filter(hash -> MD5.matcher(hash).matches())
                .toList();

        assertTrue(md5Left.isEmpty(), "MD5-hashar kvar efter V7: " + md5Left);
    }

    @Test
    void everyPasswordHashIsBcrypt() {
        hashes().forEach(hash ->
                assertTrue(BCRYPT.matcher(hash).matches(), "Inte en BCrypt-hash: " + hash));
    }

    @Test
    void seedUsersCanStillLogInWithTheirPassword() {
        // V7:s nya hash måste motsvara samma lösenord, annars låses seed-användarna ute
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        hashes().forEach(hash -> assertTrue(encoder.matches("password123", hash)));
    }

    @Test
    void oldMd5ColumnIsGone() {
        Integer columns = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'users' AND column_name = 'password_md5'
                """, Integer.class);

        assertFalse(columns > 0, "Kolumnen password_md5 finns kvar");
    }

    private static List<String> hashes() {
        return jdbc.queryForList("SELECT password_hash FROM users", String.class);
    }
}
