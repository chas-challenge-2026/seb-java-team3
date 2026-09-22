package se.comerit.seb.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import se.comerit.seb.security.JwtAuthenticationFilter;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.RoleAccessDeniedHandler;

@Configuration
public class JwtSecurityConfig {

    private final RoleAccessDeniedHandler roleAccessDeniedHandler;

    public JwtSecurityConfig(RoleAccessDeniedHandler roleAccessDeniedHandler) {
        this.roleAccessDeniedHandler = roleAccessDeniedHandler;
    }

    // Bara /api/** matchas av den här kedjan - Thymeleaf-sidorna (login, dashboard,
    // audit) matchas inte alls och rör sig därför helt
    // opåverkade, med sin egen sessionsbaserade koll precis som innan denna fil fanns.
    // @Order(1) - mer specifik än se.comerit.seb.security.SecurityConfig:s catch-all-kedja,
    // så den måste prövas först.
    @Bean
    @Order(1)
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
                // token" och 403 (via samma RoleAccessDeniedHandler som Thymeleaf-kedjan
                // använder) för "inloggad men fel roll", nu från @PreAuthorize på endpointen.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler(roleAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // Enda öppna routen - man kan inte kräva en token för att få
                        // sin första token. /api/auth/me kräver nu JWT precis som allt
                        // annat, sen steg 6 flyttade dess interna logik dit också.
                        .requestMatchers("/api/auth/login").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
