package com.example.trading.security;

/** The authenticated principal resolved from a valid session JWT - see {@link JwtAuthenticationFilter}. */
public record AuthenticatedUser(Long id, String email, String name) {
}
