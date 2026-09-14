package com.example.trading.groww;

import com.example.trading.config.TradingProperties;
import com.example.trading.enums.GrowwAuthState;
import com.example.trading.exception.AuthenticationRequiredException;
import com.example.trading.exception.GrowwConfigurationNotFoundException;
import com.example.trading.groww.dto.GrowwTokenResponse;
import com.example.trading.security.DecryptedGrowwCredentials;
import com.example.trading.service.AuditService;
import com.example.trading.service.GrowwSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GrowwAuthenticationServiceTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    private GrowwApiClient growwApiClient;
    private GrowwTokenManager tokenManager;
    private GrowwSettingsService growwSettingsService;
    private AuditService auditService;
    private TradingProperties properties;
    private GrowwAuthenticationService service;

    /** Real GrowwTokenManager backed by a fake Redis (a plain Map behind mocked calls). */
    @SuppressWarnings("unchecked")
    private static GrowwTokenManager newTokenManager() {
        Map<String, String> fakeRedis = new HashMap<>();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> fakeRedis.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            fakeRedis.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> fakeRedis.remove(inv.getArgument(0, String.class)) != null);
        return new GrowwTokenManager(redisTemplate, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @BeforeEach
    void setUp() {
        growwApiClient = mock(GrowwApiClient.class);
        tokenManager = newTokenManager();
        growwSettingsService = mock(GrowwSettingsService.class);
        auditService = mock(AuditService.class);
        properties = new TradingProperties();
        service = new GrowwAuthenticationService(growwApiClient, tokenManager, growwSettingsService, auditService, properties);

        when(growwSettingsService.loadDecryptedCredentials(USER_A))
                .thenReturn(new DecryptedGrowwCredentials("api-key-a", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"));
    }

    @Test
    void authenticate_success_storesTokenAndReportsAuthenticated() {
        GrowwTokenResponse response = new GrowwTokenResponse();
        response.setToken("live-access-token");
        response.setExpiry(null);
        when(growwApiClient.requestAccessToken(eq("api-key-a"), anyString())).thenReturn(response);

        boolean result = service.authenticate(USER_A);

        assertThat(result).isTrue();
        assertThat(service.isAuthenticated(USER_A)).isTrue();
        assertThat(service.getAccessToken(USER_A)).isEqualTo("live-access-token");
        verify(auditService).record(eq(com.example.trading.enums.AuditEventType.AUTH_SUCCESS), any(), any(), anyString());
    }

    @Test
    void authenticate_growwReturnsFailure_marksAuthFailed() {
        when(growwApiClient.requestAccessToken(eq("api-key-a"), anyString()))
                .thenThrow(com.example.trading.exception.GrowwApiException.apiError("invalid totp", "GA001"));

        boolean result = service.authenticate(USER_A);

        assertThat(result).isFalse();
        assertThat(service.isAuthenticated(USER_A)).isFalse();
        assertThat(service.getState(USER_A)).isEqualTo(GrowwAuthState.AUTH_FAILED);
        verify(auditService).record(eq(com.example.trading.enums.AuditEventType.AUTH_FAILED), any(), any(), anyString());
        verify(growwSettingsService).markDisconnected(USER_A);
    }

    @Test
    void authenticate_noSavedCredentials_failsWithoutCallingClient() {
        when(growwSettingsService.loadDecryptedCredentials(USER_B))
                .thenThrow(new GrowwConfigurationNotFoundException("No Groww configuration saved for this account yet"));

        boolean result = service.authenticate(USER_B);

        assertThat(result).isFalse();
        verifyNoInteractions(growwApiClient);
    }

    @Test
    void authenticate_alreadyAuthenticated_doesNotCallClientAgain() {
        GrowwTokenResponse response = new GrowwTokenResponse();
        response.setToken("live-access-token");
        when(growwApiClient.requestAccessToken(eq("api-key-a"), anyString())).thenReturn(response);

        service.authenticate(USER_A);
        service.authenticate(USER_A);

        verify(growwApiClient, times(1)).requestAccessToken(eq("api-key-a"), anyString());
    }

    @Test
    void getAccessToken_whenNotAuthenticated_throwsAuthenticationRequired() {
        assertThatThrownBy(() -> service.getAccessToken(USER_A)).isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void isTokenValid_falseWhenTokenExpired() {
        tokenManager.storeToken(USER_A, "x", java.time.Instant.now().minusSeconds(5));
        assertThat(service.isTokenValid(USER_A)).isFalse();
        assertThat(service.getState(USER_A)).isEqualTo(GrowwAuthState.TOKEN_EXPIRED);
    }

    @Test
    void handleAuthFailureFromBroker_marksTokenExpired_andDisconnectsInDb() {
        tokenManager.storeToken(USER_A, "x", java.time.Instant.now().plusSeconds(3600));
        service.handleAuthFailureFromBroker(USER_A);

        assertThat(service.isAuthenticated(USER_A)).isFalse();
        assertThat(service.getState(USER_A)).isEqualTo(GrowwAuthState.TOKEN_EXPIRED);
        verify(growwSettingsService).markDisconnected(USER_A);
    }

    @Test
    void clearAuthentication_resetsToAuthRequired() {
        tokenManager.storeToken(USER_A, "x", java.time.Instant.now().plusSeconds(3600));
        service.clearAuthentication(USER_A);

        assertThat(service.getState(USER_A)).isEqualTo(GrowwAuthState.AUTH_REQUIRED);
    }

    // ------------------------------------------------------------------
    // Multi-user isolation (spec Tests 1/2/6)
    // ------------------------------------------------------------------

    @Test
    void twoUsers_eachAuthenticateWithTheirOwnCredentials_getIndependentTokens() {
        when(growwSettingsService.loadDecryptedCredentials(USER_B))
                .thenReturn(new DecryptedGrowwCredentials("api-key-b", "MFRGGZDFMZTWQ2LKNNWG23TPOA"));
        GrowwTokenResponse responseA = new GrowwTokenResponse();
        responseA.setToken("token-for-user-a");
        GrowwTokenResponse responseB = new GrowwTokenResponse();
        responseB.setToken("token-for-user-b");
        when(growwApiClient.requestAccessToken(eq("api-key-a"), anyString())).thenReturn(responseA);
        when(growwApiClient.requestAccessToken(eq("api-key-b"), anyString())).thenReturn(responseB);

        service.authenticate(USER_A);
        service.authenticate(USER_B);

        assertThat(service.getAccessToken(USER_A)).isEqualTo("token-for-user-a");
        assertThat(service.getAccessToken(USER_B)).isEqualTo("token-for-user-b");
    }

    @Test
    void userAsTokenExpiring_onlyUserAsTokenIsRegenerated_userBUntouched() {
        when(growwSettingsService.loadDecryptedCredentials(USER_B))
                .thenReturn(new DecryptedGrowwCredentials("api-key-b", "MFRGGZDFMZTWQ2LKNNWG23TPOA"));
        GrowwTokenResponse responseB = new GrowwTokenResponse();
        responseB.setToken("token-for-user-b");
        when(growwApiClient.requestAccessToken(eq("api-key-b"), anyString())).thenReturn(responseB);
        service.authenticate(USER_B);

        // User A's token expires.
        tokenManager.markExpired(USER_A);
        GrowwTokenResponse freshResponseA = new GrowwTokenResponse();
        freshResponseA.setToken("fresh-token-for-user-a");
        when(growwApiClient.requestAccessToken(eq("api-key-a"), anyString())).thenReturn(freshResponseA);

        boolean reauthenticated = service.authenticate(USER_A);

        assertThat(reauthenticated).isTrue();
        assertThat(service.getAccessToken(USER_A)).isEqualTo("fresh-token-for-user-a");
        // User B's already-valid token is completely untouched by A's re-authentication -
        // B's credentials were exchanged exactly once, during B's own initial authenticate() above.
        assertThat(service.getAccessToken(USER_B)).isEqualTo("token-for-user-b");
        verify(growwApiClient, times(1)).requestAccessToken(eq("api-key-b"), anyString());
    }
}
