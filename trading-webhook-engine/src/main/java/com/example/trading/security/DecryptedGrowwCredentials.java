package com.example.trading.security;

/**
 * Decrypted Groww API key + TOTP secret for one user, held only in memory
 * for the duration of a single authentication/connection-test call. Never
 * serialized, logged, or returned in any HTTP response.
 */
public record DecryptedGrowwCredentials(String apiKey, String totpSecret) {
}
