package com.example.trading.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <jwt>}, and if it is a valid session
 * token, populates {@link SecurityContextHolder} with the {@link AuthenticatedUser}
 * it encodes. Never rejects the request itself - an absent/invalid token
 * simply leaves the security context empty, so {@code .authenticated()}
 * matchers in {@link SecurityConfig} are what actually reject the request
 * (with a uniform 401), not this filter.
 *
 * <p>Deliberately constructed inside {@link SecurityConfig} rather than
 * annotated {@code @Component}, so it is registered exactly once (inside
 * the security filter chain) instead of also being picked up a second time
 * by Spring Boot's generic servlet-filter auto-registration.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            jwtService.parseToken(token).ifPresent(user -> {
                var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }
}
