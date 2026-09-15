package se.comerit.seb.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import se.comerit.seb.security.JwtAuthenticationFilter;
import se.comerit.seb.security.JwtService;

@Configuration
public class SecurityConfig {

    // Bara /api/** matchas av den här kedjan - Thymeleaf-sidorna (login, dashboard,
    // approvals, audit, batch-upload) matchas inte alls och rör sig därför helt
    // opåverkade, med sin egen sessionsbaserade koll precis som innan denna fil fanns.
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .securityMatcher("/api/**")
                // CSRF skyddar mot att en webbläsare AUTOMATISKT skickar med sparade
                // credentials (cookies) till en attackerares sida. En Authorization-header
                // sätts aldrig automatiskt av webbläsaren, så CSRF är inte en risk här.
                .csrf(AbstractHttpConfigurer::disable)
                // STATELESS = Spring Security ska inte spara/leta efter inloggning i
                // HttpSession. Påverkar inte att Thymeleaf-sidorna fortsätter använda
                // sessionen för sitt eget bruk - det är helt Spring MVC, inte Security.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Utan det här väljer Spring Security ett standardbeteende som svarar 403
                // på en helt saknad/ogiltig token (den antar att den inte har något vettigt
                // satt att "utmana" klienten med, t.ex. en inloggningssida eller Basic-realm,
                // och 403 utan förklaring blir default). Vi vill ha 401 för "ingen/ogiltig
                // token" och sparar 403 för "inloggad men fel roll" (#115:s jobb sen).
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        // /api/auth/login måste vara öppen - man kan inte kräva en
                        // token för att få sin första token.
                        // /api/auth/me är fortfarande sessionsbaserad under huven
                        // (migreras i steg 6) - krävs inte på JWT förrän den gör det.
                        .requestMatchers("/api/auth/login", "/api/auth/me").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
