package com.example.trading.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-only-jwt-signing-secret-must-be-at-least-32-bytes-long", 1440);

    @Test
    void issueToken_thenParseToken_roundTripsTheSameUser() {
        String token = jwtService.issueToken(42L, "usera@example.com", "User A");

        var parsed = jwtService.parseToken(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().id()).isEqualTo(42L);
        assertThat(parsed.get().email()).isEqualTo("usera@example.com");
        assertThat(parsed.get().name()).isEqualTo("User A");
    }

    @Test
    void parseToken_garbageToken_returnsEmpty() {
        assertThat(jwtService.parseToken("not-a-real-jwt")).isEmpty();
    }

    @Test
    void parseToken_tokenSignedWithDifferentSecret_returnsEmpty() {
        JwtService otherService = new JwtService("a-completely-different-signing-secret-32-bytes-min", 1440);
        String token = otherService.issueToken(1L, "x@example.com", "X");

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void issueToken_alreadyExpiredAtIssuance_isRejectedOnParse() {
        // Negative validity => expiration is unambiguously in the past the
        // instant the token is issued - avoids any second-boundary-truncation
        // flakiness a near-zero validity window would introduce.
        JwtService expiredService = new JwtService("test-only-jwt-signing-secret-must-be-at-least-32-bytes-long", -1);
        String token = expiredService.issueToken(1L, "x@example.com", "X");

        assertThat(expiredService.parseToken(token)).isEmpty();
    }
}
