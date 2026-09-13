package com.example.trading.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies the stateless session JWT users get at sign-in. The
 * user's id/email/name are embedded as claims so {@link JwtAuthenticationFilter}
 * never needs a database round-trip to authenticate a request - "logout" is
 * therefore just the client discarding its token (there is no server-side
 * session to invalidate, matching a purely stateless design).
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final Duration validity;

    public JwtService(@Value("${trading.auth.jwt-secret}") String secret,
                       @Value("${trading.auth.jwt-validity-minutes:1440}") long validityMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = Duration.ofMinutes(validityMinutes);
    }

    public String issueToken(Long userId, String email, String name) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("name", name)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** Empty if the token is missing, malformed, expired, or has an invalid signature - never throws. */
    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long userId = Long.parseLong(claims.getSubject());
            return Optional.of(new AuthenticatedUser(userId, claims.get("email", String.class), claims.get("name", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
