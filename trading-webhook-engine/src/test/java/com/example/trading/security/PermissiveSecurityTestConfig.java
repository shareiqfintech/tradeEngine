package com.example.trading.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Restores the exact pre-Spring-Security behavior (everything open, no
 * CSRF) inside {@code @WebMvcTest} slices for controllers that are NOT
 * part of the new user-auth/Groww-Settings surface (see
 * {@code SecurityConfig}'s own {@code anyRequest().permitAll()} for the
 * real application - those controllers were never meant to require login).
 * Without importing this, {@code @WebMvcTest} auto-applies Spring
 * Security's own default (locked-down) filter chain inside the isolated
 * slice, since the real {@code SecurityConfig} bean is out of scope there.
 */
@TestConfiguration
public class PermissiveSecurityTestConfig {

    @Bean
    public SecurityFilterChain permissiveFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
