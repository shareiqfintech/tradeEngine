package com.example.trading.controller;

import com.example.trading.enums.SignalStatus;
import com.example.trading.exception.InvalidSignalException;
import com.example.trading.exception.TradingSessionClosedException;
import com.example.trading.redis.SignalDeduplicationService;
import com.example.trading.repository.TradingSignalRepository;
import com.example.trading.security.PermissiveSecurityTestConfig;
import com.example.trading.security.SharedSecretWebhookAuthenticator;
import com.example.trading.service.AuditService;
import com.example.trading.service.SignalValidationService;
import com.example.trading.service.TradingEngineService;
import com.example.trading.service.TradingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the webhook intake surface: authentication header,
 * JSON/bean validation, duplicate short-circuiting, and the fast-ack
 * response contract. {@code TradingEngineService} and all persistence/Redis
 * collaborators are mocked - this test is only about the controller's own
 * responsibilities, not the async engine (see {@code TradingEngineServiceTest}).
 */
@WebMvcTest(controllers = TradingViewWebhookController.class)
@Import({SharedSecretWebhookAuthenticator.class, PermissiveSecurityTestConfig.class})
class TradingViewWebhookControllerTest {

    private static final String ENDPOINT = "/api/webhook/tradingview";
    private static final String SECRET_HEADER = "X-TradingView-Secret";
    // Must match src/test/resources/application.yml -> trading.webhook.secret
    private static final String VALID_SECRET = "test-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignalValidationService signalValidationService;
    @MockBean
    private SignalDeduplicationService signalDeduplicationService;
    @MockBean
    private TradingSignalRepository signalRepository;
    @MockBean
    private TradingEngineService tradingEngineService;
    @MockBean
    private TradingSessionService tradingSessionService;
    @MockBean
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        when(signalDeduplicationService.markIfFirstSeen(anyString())).thenReturn(true);
        when(tradingSessionService.classifyNewEntry()).thenReturn(Optional.empty()); // inside the 09:25-15:10 window
    }

    private String validBuyPayload() {
        return """
                {
                  "signalId": "NIFTY-15M-001",
                  "action": "BUY",
                  "underlying": "NIFTY",
                  "exchange": "NSE",
                  "timeframe": "15m",
                  "price": 25000.50,
                  "timestamp": "2026-09-08T09:30:00+05:30"
                }
                """;
    }

    private String validSellPayload() {
        return """
                {
                  "signalId": "NIFTY-15M-002",
                  "action": "SELL",
                  "underlying": "NIFTY",
                  "exchange": "NSE",
                  "timeframe": "15m",
                  "price": 25020.00,
                  "timestamp": "2026-09-08T10:15:00+05:30"
                }
                """;
    }

    @Test
    void validBuyWebhook_returnsAccepted_andDispatchesToEngineAsync() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBuyPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.signalId").value("NIFTY-15M-001"));

        verify(signalRepository).save(argThat(entity -> entity.getSignalId().equals("NIFTY-15M-001")));
        verify(tradingEngineService).processSignalAsync("NIFTY-15M-001");
    }

    @Test
    void validSellWebhook_returnsAccepted() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSellPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.signalId").value("NIFTY-15M-002"));
    }

    @Test
    void signalAfterTradingCutoff_isPersistedRejected_andNeverDispatched() throws Exception {
        when(tradingSessionService.classifyNewEntry())
                .thenReturn(java.util.Optional.of(TradingSessionClosedException.SESSION_TRADING_CUTOFF));

        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBuyPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(signalRepository).save(argThat(e ->
                e.getStatus() == SignalStatus.REJECTED
                        && e.getRejectionReason() != null
                        && e.getRejectionReason().contains("SESSION_TRADING_CUTOFF")));
        verify(tradingEngineService, never()).processSignalAsync(anyString());
    }

    @Test
    void signalBeforeSessionStart_isPersistedRejected_withSessionNotStartedReason() throws Exception {
        when(tradingSessionService.classifyNewEntry())
                .thenReturn(java.util.Optional.of(TradingSessionClosedException.SESSION_NOT_STARTED));

        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBuyPayload()))
                .andExpect(status().isAccepted());

        verify(signalRepository).save(argThat(e ->
                e.getStatus() == SignalStatus.REJECTED
                        && e.getRejectionReason().contains("SESSION_NOT_STARTED")));
        verify(tradingEngineService, never()).processSignalAsync(anyString());
    }

    @Test
    void duplicateSignal_stillReturnsAccepted_butNeverSavesOrDispatches() throws Exception {
        when(signalDeduplicationService.markIfFirstSeen("NIFTY-15M-001")).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBuyPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(signalRepository, never()).save(any());
        verify(tradingEngineService, never()).processSignalAsync(anyString());
    }

    @Test
    void invalidSignalPerBusinessRules_returnsBadRequest() throws Exception {
        doThrow(new InvalidSignalException("Unsupported exchange: BSE"))
                .when(signalValidationService).validateStructure(ArgumentMatchers.any());

        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBuyPayload()))
                .andExpect(status().isBadRequest());

        verify(signalRepository, never()).save(any());
    }

    @Test
    void missingSecretHeader_stillAccepted() throws Exception {
        // Webhook authentication is intentionally disabled - see
        // SharedSecretWebhookAuthenticator. A request with no secret header
        // at all must be processed exactly like one with a valid header.
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBuyPayload()))
                .andExpect(status().isAccepted());
    }

    @Test
    void invalidAction_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "signalId": "NIFTY-15M-003",
                  "action": "HOLD",
                  "underlying": "NIFTY",
                  "exchange": "NSE",
                  "timeframe": "15m",
                  "price": 25000.50,
                  "timestamp": "2026-09-08T09:30:00+05:30"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingRequiredField_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "signalId": "NIFTY-15M-004",
                  "action": "BUY",
                  "exchange": "NSE",
                  "timeframe": "15m",
                  "price": 25000.50,
                  "timestamp": "2026-09-08T09:30:00+05:30"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.underlying").exists());
    }

    @Test
    void nonPositivePrice_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "signalId": "NIFTY-15M-005",
                  "action": "BUY",
                  "underlying": "NIFTY",
                  "exchange": "NSE",
                  "timeframe": "15m",
                  "price": -5,
                  "timestamp": "2026-09-08T09:30:00+05:30"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void malformedJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void blankSignalId_returnsBadRequest() throws Exception {
        String payload = """
                {
                  "signalId": "",
                  "action": "BUY",
                  "underlying": "NIFTY",
                  "exchange": "NSE",
                  "timeframe": "15m",
                  "price": 25000.50,
                  "timestamp": "2026-09-08T09:30:00+05:30"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.signalId").exists());
    }
}
