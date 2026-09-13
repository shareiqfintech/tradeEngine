package com.example.trading.service;

import com.example.trading.dto.GrowwSettingsRequest;
import com.example.trading.dto.GrowwSettingsResponse;
import com.example.trading.entity.GrowwConfigurationEntity;
import com.example.trading.exception.GrowwConfigurationNotFoundException;
import com.example.trading.groww.GrowwTokenManager;
import com.example.trading.repository.GrowwConfigurationRepository;
import com.example.trading.security.CredentialEncryptionService;
import com.example.trading.security.DecryptedGrowwCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Per-user Groww credential storage. Every method is scoped by the
 * caller-supplied {@code userId}, which must always come from the
 * authenticated principal (see {@code GrowwSettingsController}) - never
 * from a request body or path variable, so a user can never read or
 * overwrite another user's configuration. There is deliberately no
 * "load by configuration id" method at all - the only way in is "the
 * current user's own row" - so there is no id a caller could tamper with.
 */
@Service
public class GrowwSettingsService {

    private final GrowwConfigurationRepository repository;
    private final CredentialEncryptionService encryptionService;
    private final GrowwTokenManager tokenManager;

    public GrowwSettingsService(GrowwConfigurationRepository repository, CredentialEncryptionService encryptionService,
                                 GrowwTokenManager tokenManager) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.tokenManager = tokenManager;
    }

    public GrowwSettingsResponse getSettings(Long userId) {
        return repository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> GrowwSettingsResponse.builder()
                        .configured(false).apiKeyConfigured(false).totpConfigured(false).connected(false).maskedApiKey(null).build());
    }

    /**
     * Saving a NEW (non-blank) API key or TOTP secret immediately marks the
     * configuration disconnected and drops any in-memory session token for
     * this user - the old connection must never keep being trusted just
     * because it happened to still be valid seconds ago. Only an explicit,
     * successful {@code GrowwConnectionService.testConnection} call sets
     * {@code connected} back to true.
     */
    @Transactional
    public GrowwSettingsResponse saveSettings(Long userId, GrowwSettingsRequest request) {
        GrowwConfigurationEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> GrowwConfigurationEntity.forUser(userId));

        boolean credentialsChanged = false;
        if (hasValue(request.getApiKey())) {
            entity.setApiKeyEncrypted(encryptionService.encrypt(request.getApiKey().trim()));
            credentialsChanged = true;
        }
        if (hasValue(request.getTotpSecret())) {
            entity.setTotpSecretEncrypted(encryptionService.encrypt(request.getTotpSecret().trim()));
            credentialsChanged = true;
        }
        entity.setEnabled(true);
        if (credentialsChanged) {
            entity.setConnected(false);
            tokenManager.clear(userId);
        }

        GrowwConfigurationEntity saved = repository.save(entity);
        return toResponse(saved);
    }

    /** Decrypts and returns the raw credentials for internal use only (Groww authentication/"Test Connection") - never returned in any HTTP response. */
    public DecryptedGrowwCredentials loadDecryptedCredentials(Long userId) {
        GrowwConfigurationEntity entity = repository.findByUserId(userId)
                .orElseThrow(() -> new GrowwConfigurationNotFoundException("No Groww configuration saved for this account yet"));
        if (entity.getApiKeyEncrypted() == null || entity.getTotpSecretEncrypted() == null) {
            throw new GrowwConfigurationNotFoundException("Groww API key and TOTP configuration must both be saved first");
        }
        return new DecryptedGrowwCredentials(
                encryptionService.decrypt(entity.getApiKeyEncrypted()),
                encryptionService.decrypt(entity.getTotpSecretEncrypted()));
    }

    /** Set only after a real, successful Test Connection call (see GrowwConnectionService) - never as a side effect of routine re-authentication. */
    @Transactional
    public void markConnected(Long userId) {
        repository.findByUserId(userId).ifPresent(entity -> {
            entity.setConnected(true);
            entity.setLastConnectedAt(Instant.now());
            repository.save(entity);
        });
    }

    /** Set on a failed (re-)authentication attempt, or whenever credentials change - see {@link #saveSettings}. */
    @Transactional
    public void markDisconnected(Long userId) {
        repository.findByUserId(userId).ifPresent(entity -> {
            entity.setConnected(false);
            repository.save(entity);
        });
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private GrowwSettingsResponse toResponse(GrowwConfigurationEntity entity) {
        boolean apiKeyConfigured = entity.getApiKeyEncrypted() != null;
        boolean totpConfigured = entity.getTotpSecretEncrypted() != null;
        String masked = apiKeyConfigured ? maskedSuffix(encryptionService.decrypt(entity.getApiKeyEncrypted())) : null;
        return GrowwSettingsResponse.builder()
                .configured(apiKeyConfigured && totpConfigured)
                .apiKeyConfigured(apiKeyConfigured)
                .totpConfigured(totpConfigured)
                .connected(entity.isConnected())
                .maskedApiKey(masked)
                .build();
    }

    private String maskedSuffix(String value) {
        int visible = Math.min(4, value.length());
        return "*".repeat(Math.max(0, value.length() - visible)) + value.substring(value.length() - visible);
    }
}
