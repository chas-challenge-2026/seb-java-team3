package se.comerit.seb.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Endpoint-level (role) authorization is declared per-method with @PreAuthorize on the
 * controllers/services — see ApprovalApiController, ApprovalController, AuditController.
 * This class only wires the plumbing: turning the session's role into a GrantedAuthority
 * (SessionAuthenticationFilter) and handling what happens when @PreAuthorize denies access
 * (RoleAccessDeniedHandler). URL matching is intentionally left permissive here so the
 * role rules live in one declarative place instead of being duplicated between this filter
 * chain and every controller.
 *
 * This is the catch-all chain (@Order(2)) for the Thymeleaf pages — se.comerit.seb.config.
 * JwtSecurityConfig's JWT chain is @Order(1) and claims everything under /api/** first, so
 * this one only ever sees the server-rendered routes (login, dashboard, approvals, audit, ...).
 * @EnableMethodSecurity lives here since it's a single, context-wide switch: it makes
 * @PreAuthorize work for both chains, including the JWT-authenticated one.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final RoleAccessDeniedHandler roleAccessDeniedHandler;

    public SecurityConfig(SessionAuthenticationFilter sessionAuthenticationFilter,
                          RoleAccessDeniedHandler roleAccessDeniedHandler) {
        this.sessionAuthenticationFilter = sessionAuthenticationFilter;
        this.roleAccessDeniedHandler = roleAccessDeniedHandler;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF is a separate concern from role-based authorization (this story) and
                // the existing Thymeleaf forms don't send a CSRF token yet.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.accessDeniedHandler(roleAccessDeniedHandler));

        return http.build();
    }
}
