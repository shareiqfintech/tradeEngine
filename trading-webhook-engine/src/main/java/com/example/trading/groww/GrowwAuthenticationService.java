package com.example.trading.groww;

import com.example.trading.config.TradingProperties;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.GrowwAuthState;
import com.example.trading.exception.AuthenticationRequiredException;
import com.example.trading.exception.GrowwConfigurationNotFoundException;
import com.example.trading.groww.dto.GrowwTokenResponse;
import com.example.trading.security.DecryptedGrowwCredentials;
import com.example.trading.service.AuditService;
import com.example.trading.service.GrowwSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the full Groww TOTP authentication flow - the ONE place that
 * generates a TOTP code and exchanges it for an access token. Every caller
 * (the webhook-triggered trading engine, the morning/window schedulers, and
 * the Groww Settings "Test Connection" action) goes through {@link
 * #authenticate(Long)}; there is no second/parallel authentication
 * implementation anywhere else in this codebase.
 *
 * <p>{@link #authenticate(Long)} is guarded by a per-user {@link ReentrantLock}
 * so that if several requests for the SAME user arrive at once while their
 * token is missing/expired, only ONE of them actually generates a TOTP code
 * and calls Groww; concurrent callers for a DIFFERENT user are never
 * blocked by this - each user's lock is independent.
 *
 * <p>The access token itself never leaves this class: it is not logged, not
 * printed, not returned by any method here (only {@link #getAccessToken},
 * which is for {@code GrowwApiClient}'s internal use), and never serialized
 * into a REST response. Credentials are loaded fresh from {@link
 * GrowwSettingsService} (decrypted only for the duration of this call) -
 * never from a global/static field.
 */
@Slf4j
@Service
public class GrowwAuthenticationService {

    private final GrowwApiClient growwApiClient;
    private final GrowwTokenManager tokenManager;
    private final GrowwSettingsService growwSettingsService;
    private final AuditService auditService;
    private final TradingProperties properties;
    private final Map<Long, ReentrantLock> authLocksByUserId = new ConcurrentHashMap<>();

    public GrowwAuthenticationService(GrowwApiClient growwApiClient,
                                       GrowwTokenManager tokenManager,
                                       GrowwSettingsService growwSettingsService,
                                       AuditService auditService,
                                       TradingProperties properties) {
        this.growwApiClient = growwApiClient;
        this.tokenManager = tokenManager;
        this.growwSettingsService = growwSettingsService;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Runs the TOTP authentication flow against Groww using {@code userId}'s
     * OWN saved API key/TOTP secret. Safe to call concurrently for the same
     * user from many threads - only the first one actually hits the
     * network; the rest wait for it and observe its outcome. Calls for
     * different users never block each other.
     *
     * @return true if authenticated (or already authenticated), false otherwise
     */
    public boolean authenticate(Long userId) {
        ReentrantLock lock = authLocksByUserId.computeIfAbsent(userId, id -> new ReentrantLock());
        lock.lock();
        try {
            if (tokenManager.isUsable(userId)) {
                return true;
            }

            DecryptedGrowwCredentials credentials;
            try {
                credentials = growwSettingsService.loadDecryptedCredentials(userId);
            } catch (GrowwConfigurationNotFoundException ex) {
                log.warn("GROWW_AUTH_NOT_CONFIGURED userId={}", userId);
                tokenManager.markFailed(userId);
                auditService.record(AuditEventType.AUTH_FAILED, null, null, "userId=" + userId + " Groww API key/TOTP secret not configured");
                return false;
            }

            try {
                String totpCode = TotpGenerator.currentCode(credentials.totpSecret());
                GrowwTokenResponse response = growwApiClient.requestAccessToken(credentials.apiKey(), totpCode);

                if (response == null || response.getToken() == null || response.getToken().isBlank()) {
                    tokenManager.markFailed(userId);
                    growwSettingsService.markDisconnected(userId);
                    auditService.record(AuditEventType.AUTH_FAILED, null, null, "userId=" + userId + " Groww returned no access token");
                    return false;
                }

                Instant expiresAt = parseExpiry(response.getExpiry());
                tokenManager.storeToken(userId, response.getToken(), expiresAt);
                auditService.record(AuditEventType.AUTH_SUCCESS, null, null,
                        "userId=" + userId + " Groww authentication succeeded, expiresAt=" + expiresAt);
                return true;
            } catch (Exception ex) {
                log.warn("GROWW_AUTH_FAILED userId={} reason={}", userId, ex.getMessage());
                tokenManager.markFailed(userId);
                growwSettingsService.markDisconnected(userId);
                auditService.record(AuditEventType.AUTH_FAILED, null, null,
                        "userId=" + userId + " Groww authentication failed: " + safeMessage(ex));
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    /** @throws AuthenticationRequiredException if no usable token exists for this user. */
    public String getAccessToken(Long userId) {
        return tokenManager.getToken(userId)
                .orElseThrow(() -> new AuthenticationRequiredException("Groww access token is not available"));
    }

    public boolean isAuthenticated(Long userId) {
        return tokenManager.isUsable(userId);
    }

    public boolean isTokenValid(Long userId) {
        return tokenManager.getState(userId) == GrowwAuthState.AUTHENTICATED && tokenManager.isUsable(userId);
    }

    public GrowwAuthState getState(Long userId) {
        return tokenManager.getState(userId);
    }

    /** Invoked by GrowwApiClient/TradingEngineService when Groww responds 401/403 to a live call for this user. */
    public void handleAuthFailureFromBroker(Long userId) {
        tokenManager.markExpired(userId);
        growwSettingsService.markDisconnected(userId);
        auditService.record(AuditEventType.TOKEN_EXPIRED, null, null, "userId=" + userId + " Groww rejected the access token (401/403)");
    }

    public void clearAuthentication(Long userId) {
        tokenManager.clear(userId);
    }

    private Instant parseExpiry(String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(expiry).toInstant();
        } catch (Exception ignoredOffset) {
            try {
                return java.time.LocalDateTime.parse(expiry, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atZone(properties.getZoneId())
                        .toInstant();
            } catch (Exception ignoredLocal) {
                log.warn("GROWW_AUTH_EXPIRY_UNPARSEABLE value={}", expiry);
                return null;
            }
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }
}
