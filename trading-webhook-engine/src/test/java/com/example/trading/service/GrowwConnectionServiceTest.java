package com.example.trading.service;

import com.example.trading.dto.GrowwConnectionTestResponse;
import com.example.trading.exception.GrowwApiException;
import com.example.trading.exception.GrowwConfigurationNotFoundException;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.groww.dto.GrowwPositionDto;
import com.example.trading.security.DecryptedGrowwCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code GrowwSettingsService}/{@code GrowwAuthenticationService} are
 * mocked here (their own behavior is covered by {@link GrowwSettingsServiceTest}
 * and {@code GrowwAuthenticationServiceTest}) so this test is purely about
 * the connection-test orchestration: it must delegate to the ONE existing
 * {@link GrowwAuthenticationService#authenticate(Long)} implementation
 * (never re-implement TOTP/auth itself), then make one more real Groww API
 * call to verify the token actually works, and never fabricate a success.
 */
class GrowwConnectionServiceTest {

    private static final Long USER_A = 1L;

    private GrowwSettingsService growwSettingsService;
    private GrowwAuthenticationService growwAuthenticationService;
    private GrowwApiClient growwApiClient;
    private GrowwConnectionService service;

    @BeforeEach
    void setUp() {
        growwSettingsService = mock(GrowwSettingsService.class);
        growwAuthenticationService = mock(GrowwAuthenticationService.class);
        growwApiClient = mock(GrowwApiClient.class);
        service = new GrowwConnectionService(growwSettingsService, growwAuthenticationService, growwApiClient);
        when(growwSettingsService.loadDecryptedCredentials(USER_A))
                .thenReturn(new DecryptedGrowwCredentials("user-a-api-key", "JBSWY3DPEHPK3PXP"));
    }

    @Test
    void testConnection_success_delegatesToExistingAuthenticationServiceThenVerifiesWithARealApiCall() {
        when(growwAuthenticationService.authenticate(USER_A)).thenReturn(true);
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.<GrowwPositionDto>of());

        GrowwConnectionTestResponse response = service.testConnection(USER_A);

        assertThat(response.isConnected()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Groww account connected successfully");
        verify(growwAuthenticationService).clearAuthentication(USER_A); // always re-verifies, never trusts a stale cached token
        verify(growwAuthenticationService).authenticate(USER_A);
        verify(growwApiClient).getPositions(USER_A, "FNO");
        verify(growwSettingsService).markConnected(USER_A);
    }

    @Test
    void testConnection_authenticationFails_returnsFailureWithoutExposingDetails_andNeverCallsPositions() {
        when(growwAuthenticationService.authenticate(USER_A)).thenReturn(false);

        GrowwConnectionTestResponse response = service.testConnection(USER_A);

        assertThat(response.isConnected()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Unable to connect to Groww account");
        verifyNoInteractions(growwApiClient); // never proceeds to the verification call after a failed auth
        verify(growwSettingsService, never()).markConnected(any());
    }

    @Test
    void testConnection_authSucceedsButVerificationCallFails_isTreatedAsFailure_neverFakesSuccess() {
        when(growwAuthenticationService.authenticate(USER_A)).thenReturn(true);
        when(growwApiClient.getPositions(USER_A, "FNO")).thenThrow(GrowwApiException.apiError("Session invalid", "GA020"));

        GrowwConnectionTestResponse response = service.testConnection(USER_A);

        assertThat(response.isConnected()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Unable to connect to Groww account");
        verify(growwSettingsService).markDisconnected(USER_A);
        verify(growwSettingsService, never()).markConnected(any());
    }

    @Test
    void testConnection_noSavedCredentials_failsFastWithoutAttemptingAuthentication() {
        when(growwSettingsService.loadDecryptedCredentials(2L))
                .thenThrow(new GrowwConfigurationNotFoundException("No Groww configuration saved for this account yet"));

        GrowwConnectionTestResponse response = service.testConnection(2L);

        assertThat(response.isConnected()).isFalse();
        verifyNoInteractions(growwAuthenticationService);
        verifyNoInteractions(growwApiClient);
    }
}
