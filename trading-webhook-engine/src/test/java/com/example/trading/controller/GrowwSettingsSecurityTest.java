package com.example.trading.controller;

import com.example.trading.dto.GrowwSettingsResponse;
import com.example.trading.security.JwtService;
import com.example.trading.security.SecurityConfig;
import com.example.trading.security.SharedSecretWebhookAuthenticator;
import com.example.trading.service.GrowwConnectionService;
import com.example.trading.service.GrowwSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (within the servlet layer) proof that the REAL
 * {@link SecurityConfig}/{@code JwtAuthenticationFilter} chain - not just
 * the service-layer unit tests - actually rejects unauthenticated Groww
 * Settings requests, and that a valid token resolves to the correct user
 * whose id is what actually gets passed down to the service layer (never
 * anything from the request itself).
 */
@WebMvcTest(controllers = GrowwSettingsController.class)
@Import({SecurityConfig.class, JwtService.class, SharedSecretWebhookAuthenticator.class})
class GrowwSettingsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private GrowwSettingsService growwSettingsService;

    @MockBean
    private GrowwConnectionService growwConnectionService;

    @Test
    void getSettings_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/settings/groww"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSettings_withGarbageToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/settings/groww").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSettings_withValidToken_loadsOnlyThatUsersConfiguration() throws Exception {
        String tokenForUserA = jwtService.issueToken(1L, "usera@example.com", "User A");
        when(growwSettingsService.getSettings(1L)).thenReturn(
                GrowwSettingsResponse.builder().configured(true).apiKeyConfigured(true).totpConfigured(true).maskedApiKey("****1234").build());

        mockMvc.perform(get("/api/settings/groww").header("Authorization", "Bearer " + tokenForUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.maskedApiKey").value("****1234"));

        verify(growwSettingsService).getSettings(eq(1L));
    }

    @Test
    void getSettings_tokenForUserB_neverLoadsUserAsConfiguration() throws Exception {
        String tokenForUserB = jwtService.issueToken(2L, "userb@example.com", "User B");
        when(growwSettingsService.getSettings(2L)).thenReturn(
                GrowwSettingsResponse.builder().configured(true).apiKeyConfigured(true).totpConfigured(true).maskedApiKey("****5678").build());

        mockMvc.perform(get("/api/settings/groww").header("Authorization", "Bearer " + tokenForUserB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedApiKey").value("****5678"));

        verify(growwSettingsService).getSettings(eq(2L));
        // No mechanism in this request could ever cause user 1's configuration to load - there is
        // no userId/configId anywhere in the request body/path for GrowwSettingsController to trust.
    }
}
