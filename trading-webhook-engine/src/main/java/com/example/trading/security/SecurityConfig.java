package com.example.trading.security;

import com.example.trading.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Stateless JWT authentication for the user-facing API surface (sign up/in,
 * {@code /api/settings/groww/**}, and every {@code /api/trading/**}
 * endpoint that resolves a specific user's own Groww credentials/token -
 * positions, risk, F&O config, status, and the manual "authenticate now"
 * action). The TradingView webhook keeps its own, separate,
 * {@code WebhookAuthenticationFilter} shared-secret check, unaffected by
 * this - it is never tied to a specific application user (see
 * {@code GrowwUserResolver}). Endpoints that only read local DB data and
 * never touch a user's Groww credentials (signals, orders, audit,
 * kill-switch/pause/resume) remain exactly as open as before, since
 * locking those down is out of scope for this task.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication in this app is entirely custom (JwtAuthenticationFilter
     * populates SecurityContextHolder directly from a validated JWT;
     * sign-in checks BCrypt hashes against UserRepository directly) - this
     * app never uses Spring Security's UserDetailsService/AuthenticationManager
     * machinery at all. Without SOME UserDetailsService bean present though,
     * Spring Boot's default auto-configuration generates a random
     * "default user" password on every startup (logged, unused, harmless,
     * but noisy) - this empty, no-users bean just suppresses that.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers("/api/webhook/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/settings/groww/**").authenticated()
                        // These resolve and use a specific user's own Groww
                        // credentials/token - never a global/shared one - so
                        // they must know who is calling.
                        .requestMatchers("/api/trading/status", "/api/trading/groww/authenticate",
                                "/api/trading/positions", "/api/trading/positions/**",
                                "/api/trading/risk", "/api/trading/fno/**").authenticated()
                        // Everything else (signals, orders, audit, kill-switch/pause/resume -
                        // none of which touch a user's Groww credentials) is out of scope for
                        // this task and stays exactly as open as it was before Spring Security
                        // was introduced.
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(this::writeUnauthorized))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response,
                                     org.springframework.security.core.AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
