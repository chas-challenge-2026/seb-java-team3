package se.comerit.seb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// En enda lösenordshashning för hela applikationen: inloggningen (AuthService) jämför mot den, och
// användaradministrationen hashar nya lösenord med den. Då kan de två aldrig komma ur takt.
@Configuration
public class PasswordEncoderConfig {

    // Kostnadsfaktor 12 betyder 2^12 varv. Det är medvetet långsamt, så att den som får tag på databasen
    // inte kan gissa lösenord i någon rimlig takt. Samma faktor som seed-hasharna från V7 har. Äldre hashar
    // med en annan faktor fungerar fortfarande vid inloggning, eftersom faktorn ligger i själva hashen.
    static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
