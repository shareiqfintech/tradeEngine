package com.example.trading.controller;

import com.example.trading.config.TradingProperties;
import com.example.trading.enums.SessionState;
import com.example.trading.enums.TradingMode;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.repository.OrderRepository;
import com.example.trading.security.JwtService;
import com.example.trading.security.SecurityConfig;
import com.example.trading.security.SharedSecretWebhookAuthenticator;
import com.example.trading.service.GrowwPositionService;
import com.example.trading.service.TradingSessionService;
import com.example.trading.service.TradingStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the operator control surface. Confirms the status
 * endpoint never serializes anything beyond {@code TradingStatusResponse}'s
 * declared fields - in particular no API key/TOTP secret/access token - and
 * that it (like {@code /groww/authenticate}) only reflects the CALLING
 * user's own Groww state, resolved from a real session JWT.
 */
@WebMvcTest(controllers = TradingAdminController.class)
@Import({SharedSecretWebhookAuthenticator.class, SecurityConfig.class, JwtService.class})
class TradingAdminControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private TradingStateService tradingStateService;
    @MockBean
    private TradingSessionService tradingSessionService;
    @MockBean
    private GrowwAuthenticationService growwAuthenticationService;
    @MockBean
    private OrderRepository orderRepository;
    @MockBean
    private GrowwPositionService growwPositionService;
    @MockBean
    private TradingProperties properties;

    private String tokenFor(Long userId) {
        return jwtService.issueToken(userId, "user" + userId + "@example.com", "Test User " + userId);
    }

    @BeforeEach
    void sessionStubs() {
        when(properties.getMarket()).thenReturn(new TradingProperties.Market());
        when(properties.getTimezone()).thenReturn("Asia/Kolkata");
        when(tradingSessionService.isNewEntryAllowed()).thenReturn(true);
        when(tradingSessionService.getEffectiveState(org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(SessionState.TRADING_ACTIVE);
    }

    @Test
    void enableKillSwitch_invokesTradingStateService() throws Exception {
        mockMvc.perform(post("/api/trading/kill-switch/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("KILL_SWITCH_ENABLED"));

        verify(tradingStateService).enableKillSwitch();
    }

    @Test
    void disableKillSwitch_invokesTradingStateService() throws Exception {
        mockMvc.perform(post("/api/trading/kill-switch/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("KILL_SWITCH_DISABLED"));

        verify(tradingStateService).disableKillSwitch();
    }

    @Test
    void pause_invokesTradingStateService() throws Exception {
        mockMvc.perform(post("/api/trading/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        verify(tradingStateService).pause();
    }

    @Test
    void resume_invokesTradingStateService() throws Exception {
        mockMvc.perform(post("/api/trading/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESUMED"));

        verify(tradingStateService).resume();
    }

    @Test
    void status_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/trading/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void status_neverExposesCredentials() throws Exception {
        when(properties.getMode()).thenReturn(TradingMode.PAPER);
        when(properties.getZoneId()).thenReturn(java.time.ZoneId.of("Asia/Kolkata"));
        when(tradingStateService.isTradingAllowed()).thenReturn(true);
        when(growwAuthenticationService.isAuthenticated(USER_ID)).thenReturn(true);
        when(tradingStateService.isKillSwitchEnabled()).thenReturn(false);
        when(tradingStateService.isPaused()).thenReturn(false);
        when(orderRepository.countByCreatedAtBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(3L);
        when(growwPositionService.getPositions(USER_ID)).thenReturn(java.util.List.of());

        String body = mockMvc.perform(get("/api/trading/status").header("Authorization", "Bearer " + tokenFor(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PAPER"))
                .andExpect(jsonPath("$.tradingEnabled").value(true))
                .andExpect(jsonPath("$.growwAuthenticated").value(true))
                .andExpect(jsonPath("$.killSwitch").value(false))
                .andExpect(jsonPath("$.paused").value(false))
                .andExpect(jsonPath("$.ordersToday").value(3))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body.toLowerCase())
                .doesNotContain("apikey").doesNotContain("api_key")
                .doesNotContain("totp").doesNotContain("token").doesNotContain("secret");
    }

    @Test
    void status_differentUsers_resolveIndependentGrowwState() throws Exception {
        when(properties.getMode()).thenReturn(TradingMode.PAPER);
        when(properties.getZoneId()).thenReturn(java.time.ZoneId.of("Asia/Kolkata"));
        when(tradingStateService.isTradingAllowed()).thenReturn(true);
        when(orderRepository.countByCreatedAtBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        when(growwAuthenticationService.isAuthenticated(1L)).thenReturn(true);
        when(growwAuthenticationService.isAuthenticated(2L)).thenReturn(false);
        when(growwPositionService.getPositions(1L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/trading/status").header("Authorization", "Bearer " + tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.growwAuthenticated").value(true));

        mockMvc.perform(get("/api/trading/status").header("Authorization", "Bearer " + tokenFor(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.growwAuthenticated").value(false))
                .andExpect(jsonPath("$.openPositions").value(0));
    }
}
