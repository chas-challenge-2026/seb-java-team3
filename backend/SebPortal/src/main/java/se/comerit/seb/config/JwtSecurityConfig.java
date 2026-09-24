package se.comerit.seb.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import se.comerit.seb.security.JwtAuthenticationFilter;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.RoleAccessDeniedHandler;

// @EnableMethodSecurity gör att @PreAuthorize på controllers/services faktiskt kontrolleras.
@Configuration
@EnableMethodSecurity
public class JwtSecurityConfig {

    private final RoleAccessDeniedHandler roleAccessDeniedHandler;

    public JwtSecurityConfig(RoleAccessDeniedHandler roleAccessDeniedHandler) {
        this.roleAccessDeniedHandler = roleAccessDeniedHandler;
    }

    // Bara /api/** matchas av den här kedjan. Allt API är JWT-skyddat; övriga sökvägar
    // (t.ex. statiska frontend-filer) omfattas inte av någon kedja.
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .securityMatcher("/api/**")
                // CSRF skyddar mot att en webbläsare AUTOMATISKT skickar med sparade
                // credentials (cookies) till en attackerares sida. En Authorization-header
                // sätts aldrig automatiskt av webbläsaren, så CSRF är inte en risk här.
                .csrf(AbstractHttpConfigurer::disable)
                // STATELESS = Spring Security ska inte spara/leta efter inloggning i
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Utan det här väljer Spring Security ett standardbeteende som svarar 403
                // på en helt saknad/ogiltig token (den antar att den inte har något vettigt
                // satt att "utmana" klienten med, t.ex. en inloggningssida eller Basic-realm,
                // och 403 utan förklaring blir default). Vi vill ha 401 för "ingen/ogiltig
                // token" och 403 (via RoleAccessDeniedHandler) för "inloggad men fel roll",
                // från @PreAuthorize på endpointen.
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
