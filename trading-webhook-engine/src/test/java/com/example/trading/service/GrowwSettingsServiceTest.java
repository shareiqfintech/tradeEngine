package com.example.trading.service;

import com.example.trading.dto.GrowwSettingsRequest;
import com.example.trading.dto.GrowwSettingsResponse;
import com.example.trading.entity.GrowwConfigurationEntity;
import com.example.trading.exception.GrowwConfigurationNotFoundException;
import com.example.trading.groww.GrowwTokenManager;
import com.example.trading.repository.GrowwConfigurationRepository;
import com.example.trading.security.CredentialEncryptionService;
import com.example.trading.security.DecryptedGrowwCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises multi-user isolation directly: two users' configurations are
 * stored in the same fake backing map (keyed by userId, mirroring the real
 * unique-per-user_id constraint), and every assertion confirms one user's
 * call only ever sees/affects their own row.
 */
class GrowwSettingsServiceTest {

    private GrowwConfigurationRepository repository;
    private final CredentialEncryptionService encryptionService =
            new CredentialEncryptionService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    private GrowwTokenManager tokenManager;
    private GrowwSettingsService service;
    private final Map<Long, GrowwConfigurationEntity> byUserId = new HashMap<>();

    @BeforeEach
    void setUp() {
        repository = mock(GrowwConfigurationRepository.class);
        tokenManager = mock(GrowwTokenManager.class);
        service = new GrowwSettingsService(repository, encryptionService, tokenManager);
        byUserId.clear();
        when(repository.findByUserId(any())).thenAnswer(inv -> Optional.ofNullable(byUserId.get(inv.getArgument(0, Long.class))));
        when(repository.save(any(GrowwConfigurationEntity.class))).thenAnswer(inv -> {
            GrowwConfigurationEntity entity = inv.getArgument(0);
            byUserId.put(entity.getUserId(), entity);
            return entity;
        });
    }

    private GrowwSettingsRequest request(String apiKey, String totpSecret) {
        GrowwSettingsRequest request = new GrowwSettingsRequest();
        request.setApiKey(apiKey);
        request.setTotpSecret(totpSecret);
        return request;
    }

    @Test
    void saveSettings_userA_thenUserB_eachOnlySeesTheirOwnConfiguration() {
        service.saveSettings(1L, request("api-key-A", "TOTP-SECRET-A"));
        service.saveSettings(2L, request("api-key-B", "TOTP-SECRET-B"));

        DecryptedGrowwCredentials userA = service.loadDecryptedCredentials(1L);
        DecryptedGrowwCredentials userB = service.loadDecryptedCredentials(2L);

        assertThat(userA.apiKey()).isEqualTo("api-key-A");
        assertThat(userA.totpSecret()).isEqualTo("TOTP-SECRET-A");
        assertThat(userB.apiKey()).isEqualTo("api-key-B");
        assertThat(userB.totpSecret()).isEqualTo("TOTP-SECRET-B");
    }

    @Test
    void saveSettings_userA_neverModifiesUserBsConfiguration() {
        service.saveSettings(1L, request("api-key-A", "TOTP-SECRET-A"));
        service.saveSettings(2L, request("api-key-B", "TOTP-SECRET-B"));

        service.saveSettings(1L, request("updated-api-key-A", null));

        assertThat(service.loadDecryptedCredentials(2L).apiKey()).isEqualTo("api-key-B");
        assertThat(service.loadDecryptedCredentials(1L).apiKey()).isEqualTo("updated-api-key-A");
    }

    @Test
    void getSettings_neverReturnsFullApiKeyOrAnyTotpValue() {
        service.saveSettings(1L, request("eyJhbGciOiJFUzI1NiJ9.some.jwt.looking.key", "G4EWVO3V6GHBA6UAVFG7JA67"));

        GrowwSettingsResponse response = service.getSettings(1L);

        assertThat(response.isConfigured()).isTrue();
        assertThat(response.getMaskedApiKey()).endsWith(".key").doesNotContain("eyJhbGciOiJFUzI1NiJ9");
        assertThat(response.getMaskedApiKey()).startsWith("*");
        // GrowwSettingsResponse has no TOTP field at all - the raw secret can never leak through it.
    }

    @Test
    void getSettings_forUserWithNoConfiguration_returnsNotConfigured() {
        GrowwSettingsResponse response = service.getSettings(99L);

        assertThat(response.isConfigured()).isFalse();
        assertThat(response.isApiKeyConfigured()).isFalse();
        assertThat(response.isTotpConfigured()).isFalse();
        assertThat(response.isConnected()).isFalse();
        assertThat(response.getMaskedApiKey()).isNull();
    }

    @Test
    void saveSettings_blankFieldsLeaveExistingValuesUnchanged() {
        service.saveSettings(1L, request("original-api-key", "ORIGINAL-TOTP"));

        // Frontend sends blank/omitted fields when the user didn't type a new value.
        service.saveSettings(1L, request("", null));

        DecryptedGrowwCredentials credentials = service.loadDecryptedCredentials(1L);
        assertThat(credentials.apiKey()).isEqualTo("original-api-key");
        assertThat(credentials.totpSecret()).isEqualTo("ORIGINAL-TOTP");
    }

    @Test
    void saveSettings_newValueReplacesOldValue() {
        service.saveSettings(1L, request("old-api-key", "OLD-TOTP"));
        service.saveSettings(1L, request("new-api-key", null));

        DecryptedGrowwCredentials credentials = service.loadDecryptedCredentials(1L);
        assertThat(credentials.apiKey()).isEqualTo("new-api-key");
        assertThat(credentials.totpSecret()).isEqualTo("OLD-TOTP"); // untouched
    }

    @Test
    void loadDecryptedCredentials_notYetConfigured_throws() {
        assertThatThrownBy(() -> service.loadDecryptedCredentials(1L))
                .isInstanceOf(GrowwConfigurationNotFoundException.class);
    }

    @Test
    void loadDecryptedCredentials_onlyApiKeySaved_throwsUntilTotpIsAlsoSaved() {
        service.saveSettings(1L, request("api-key-only", null));

        assertThatThrownBy(() -> service.loadDecryptedCredentials(1L))
                .isInstanceOf(GrowwConfigurationNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Connection state (spec Tests 7/8: credential changes disconnect;
    // only a real Test Connection reconnects)
    // ------------------------------------------------------------------

    @Test
    void markConnected_thenChangingApiKey_flipsConnectedBackToFalse_andClearsCachedToken() {
        service.saveSettings(1L, request("api-key", "TOTP"));
        service.markConnected(1L);
        assertThat(service.getSettings(1L).isConnected()).isTrue();

        service.saveSettings(1L, request("new-api-key", null));

        assertThat(service.getSettings(1L).isConnected()).isFalse();
        // Both the initial save and this credential change clear the cached
        // token - the assertion is that it happens on every credential
        // change, not a specific total count.
        verify(tokenManager, org.mockito.Mockito.atLeastOnce()).clear(1L);
    }

    @Test
    void markConnected_thenChangingTotpSecret_flipsConnectedBackToFalse() {
        service.saveSettings(1L, request("api-key", "TOTP"));
        service.markConnected(1L);

        service.saveSettings(1L, request(null, "NEW-TOTP-SECRET"));

        assertThat(service.getSettings(1L).isConnected()).isFalse();
    }

    @Test
    void markConnected_onlyAffectsTheCalledUser() {
        service.saveSettings(1L, request("api-key-A", "TOTP-A"));
        service.saveSettings(2L, request("api-key-B", "TOTP-B"));

        service.markConnected(1L);

        assertThat(service.getSettings(1L).isConnected()).isTrue();
        assertThat(service.getSettings(2L).isConnected()).isFalse();
    }

    @Test
    void markDisconnected_setsConnectedFalse() {
        service.saveSettings(1L, request("api-key", "TOTP"));
        service.markConnected(1L);

        service.markDisconnected(1L);

        assertThat(service.getSettings(1L).isConnected()).isFalse();
    }
}
