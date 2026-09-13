package com.example.trading.service;

import com.example.trading.dto.GrowwConnectionTestResponse;
import com.example.trading.exception.GrowwApiException;
import com.example.trading.exception.GrowwConfigurationNotFoundException;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.GrowwAuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * "Test Groww Connection" for a specific user's saved credentials. Reuses
 * the EXISTING {@link GrowwAuthenticationService#authenticate(Long)} - the
 * one and only TOTP/Groww authentication implementation in this codebase -
 * rather than re-running the TOTP exchange itself, then makes one more real
 * Groww API call to prove the resulting token actually works (Groww could
 * conceivably issue a token that then fails on first real use).
 */
@Slf4j
@Service
public class GrowwConnectionService {

    private static final String SEGMENT_FNO = "FNO";

    private final GrowwSettingsService growwSettingsService;
    private final GrowwAuthenticationService growwAuthenticationService;
    private final GrowwApiClient growwApiClient;

    public GrowwConnectionService(GrowwSettingsService growwSettingsService,
                                   GrowwAuthenticationService growwAuthenticationService,
                                   GrowwApiClient growwApiClient) {
        this.growwSettingsService = growwSettingsService;
        this.growwAuthenticationService = growwAuthenticationService;
        this.growwApiClient = growwApiClient;
    }

    public GrowwConnectionTestResponse testConnection(Long userId) {
        // Fails fast with a clear reason before even attempting to authenticate, rather than a generic "unable to connect".
        try {
            growwSettingsService.loadDecryptedCredentials(userId);
        } catch (GrowwConfigurationNotFoundException ex) {
            return new GrowwConnectionTestResponse(false, ex.getMessage());
        }

        // Always re-authenticate for an explicit connection test, even if a
        // still-valid token happens to already be cached for this user -
        // the whole point of this action is to verify right now.
        growwAuthenticationService.clearAuthentication(userId);
        boolean authenticated = growwAuthenticationService.authenticate(userId);
        if (!authenticated) {
            return new GrowwConnectionTestResponse(false, "Unable to connect to Groww account");
        }

        try {
            // Proves the token actually works against a real Groww API, not just that one was issued.
            growwApiClient.getPositions(userId, SEGMENT_FNO);
            growwSettingsService.markConnected(userId);
            log.info("GROWW_CONNECTION_TEST_SUCCEEDED userId={}", userId);
            return new GrowwConnectionTestResponse(true, "Groww account connected successfully");
        } catch (GrowwApiException ex) {
            log.warn("GROWW_CONNECTION_TEST_FAILED userId={} authError={}", userId, ex.isAuthError());
            growwSettingsService.markDisconnected(userId);
            return new GrowwConnectionTestResponse(false, "Unable to connect to Groww account");
        }
    }
}
