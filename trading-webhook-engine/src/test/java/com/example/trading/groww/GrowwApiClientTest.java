package com.example.trading.groww;

import com.example.trading.config.TradingProperties;
import com.example.trading.exception.GrowwApiException;
import com.example.trading.groww.dto.GrowwOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link GrowwApiClient} end-to-end against a real (but local,
 * in-JVM) HTTP server - using {@code com.sun.net.httpserver.HttpServer}
 * (JDK built-in, no extra test dependency) rather than mocking WebClient's
 * fluent API, so header construction, envelope parsing, and HTTP-status
 * error handling are all genuinely exercised.
 */
class GrowwApiClientTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    private HttpServer server;
    private GrowwApiClient client;
    private TradingProperties properties;
    private GrowwTokenManager tokenManager;
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> lastApiVersionHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = server.getAddress().getPort();

        properties = new TradingProperties();
        properties.getGroww().setBaseUrl("http://localhost:" + port);
        properties.getGroww().setApiVersion("1.0");

        tokenManager = new GrowwTokenManager();

        WebClient apiClient = WebClient.builder().baseUrl(properties.getGroww().getBaseUrl()).build();
        WebClient assetClient = WebClient.builder().build();
        client = new GrowwApiClient(apiClient, assetClient, properties, tokenManager, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createOrder_success_parsesEnvelopeAndReturnsPayload() throws IOException {
        tokenManager.storeToken(USER_A, "session-token", Instant.now().plusSeconds(3600));
        server.createContext("/v1/order/create", exchange -> {
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastApiVersionHeader.set(exchange.getRequestHeaders().getFirst("X-API-VERSION"));
            String body = "{\"status\":\"SUCCESS\",\"payload\":{\"groww_order_id\":\"GID123\","
                    + "\"order_status\":\"OPEN\",\"order_reference_id\":\"TV-NIFTY-ABC12345\"}}";
            sendJson(exchange, 200, body);
        });
        server.start();

        var response = client.createOrder(USER_A, GrowwOrderRequest.builder()
                .tradingSymbol("NIFTY25SEP25000CE")
                .quantity(75)
                .validity("DAY")
                .exchange("NSE")
                .segment("FNO")
                .product("NRML")
                .orderType("MARKET")
                .transactionType("BUY")
                .orderReferenceId("TV-NIFTY-ABC12345")
                .build());

        assertThat(response.getGrowwOrderId()).isEqualTo("GID123");
        assertThat(response.getOrderStatus()).isEqualTo("OPEN");
        assertThat(lastAuthHeader.get()).isEqualTo("Bearer session-token");
        assertThat(lastApiVersionHeader.get()).isEqualTo("1.0");
    }

    @Test
    void createOrder_growwReturnsFailureEnvelope_throwsGrowwApiException() throws IOException {
        tokenManager.storeToken(USER_A, "session-token", Instant.now().plusSeconds(3600));
        server.createContext("/v1/order/create", exchange ->
                sendJson(exchange, 200, "{\"status\":\"FAILURE\",\"error\":{\"code\":\"GA001\",\"message\":\"Invalid trading symbol\"}}"));
        server.start();

        assertThatThrownBy(() -> client.createOrder(USER_A, GrowwOrderRequest.builder().tradingSymbol("BAD").build()))
                .isInstanceOf(GrowwApiException.class)
                .hasMessageContaining("Invalid trading symbol");
    }

    @Test
    void createOrder_http401_isReportedAsAuthError() throws IOException {
        tokenManager.storeToken(USER_A, "session-token", Instant.now().plusSeconds(3600));
        server.createContext("/v1/order/create", exchange ->
                sendJson(exchange, 401, "{\"status\":\"FAILURE\",\"error\":{\"code\":\"GA005\",\"message\":\"Unauthorized\"}}"));
        server.start();

        assertThatThrownBy(() -> client.createOrder(USER_A, GrowwOrderRequest.builder().tradingSymbol("X").build()))
                .isInstanceOf(GrowwApiException.class)
                .satisfies(ex -> assertThat(((GrowwApiException) ex).isAuthError()).isTrue());
    }

    @Test
    void createOrder_withoutUsableToken_throwsAuthErrorBeforeSendingRequest() {
        assertThatThrownBy(() -> client.createOrder(USER_A, GrowwOrderRequest.builder().tradingSymbol("X").build()))
                .isInstanceOf(GrowwApiException.class)
                .satisfies(ex -> assertThat(((GrowwApiException) ex).isAuthError()).isTrue());
    }

    @Test
    void requestAccessToken_usesGivenApiKeyBearerNotSessionToken() throws IOException {
        // Per Groww's docs, the TOTP auth response is NOT wrapped in the
        // {status/payload/error} envelope every other endpoint uses - it's
        // a flat {token, tokenRefId, sessionName, expiry, isActive} object.
        server.createContext("/v1/token/api/access", exchange -> {
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, 200, "{\"token\":\"new-token\",\"tokenRefId\":\"ref-123\",\"isActive\":true}");
        });
        server.start();

        var response = client.requestAccessToken("test-api-key", "123456");

        assertThat(response.getToken()).isEqualTo("new-token");
        assertThat(lastAuthHeader.get()).isEqualTo("Bearer test-api-key");
    }

    @Test
    void requestAccessToken_growwErrorEnvelope_usesErrorCodeAndErrorMessageAliases() throws IOException {
        // Real observed Groww error shape for this endpoint uses
        // "errorCode"/"errorMessage", not the documented "code"/"message" -
        // GrowwErrorBody must accept both via @JsonAlias.
        server.createContext("/v1/token/api/access", exchange ->
                sendJson(exchange, 400, "{\"header\":null,\"error\":{\"errorCode\":\"400\",\"errorMessage\":\"Token key not found or inactive\"},\"response\":null}"));
        server.start();

        assertThatThrownBy(() -> client.requestAccessToken("test-api-key", "123456"))
                .isInstanceOf(GrowwApiException.class)
                .hasMessageContaining("Token key not found or inactive")
                .satisfies(ex -> assertThat(((GrowwApiException) ex).getGrowwErrorCode()).isEqualTo("400"));
    }

    @Test
    void getPositions_mapsRealPositionsWrapperPayload() throws IOException {
        // Real Groww response wraps the array as payload:{"positions":[...]} -
        // NOT a bare array. This is the exact shape from Groww's documented example.
        tokenManager.storeToken(USER_A, "session-token", Instant.now().plusSeconds(3600));
        server.createContext("/v1/positions/user", exchange -> sendJson(exchange, 200,
                "{\"status\":\"SUCCESS\",\"payload\":{\"positions\":[{\"trading_symbol\":\"NIFTY25SEP25000CE\",\"quantity\":75,\"product\":\"NRML\"}]}}"));
        server.start();

        var positions = client.getPositions(USER_A, "FNO");

        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
    }

    @Test
    void getPositions_emptyPositionsArray_returnsEmptyList() throws IOException {
        tokenManager.storeToken(USER_A, "session-token", Instant.now().plusSeconds(3600));
        server.createContext("/v1/positions/user", exchange -> sendJson(exchange, 200,
                "{\"status\":\"SUCCESS\",\"payload\":{\"positions\":[]}}"));
        server.start();

        var positions = client.getPositions(USER_A, "FNO");

        assertThat(positions).isEmpty();
    }

    @Test
    void getPositionForTradingSymbol_mapsRealPositionsWrapperPayload() throws IOException {
        tokenManager.storeToken(USER_A, "session-token", Instant.now().plusSeconds(3600));
        server.createContext("/v1/positions/trading-symbol", exchange -> sendJson(exchange, 200,
                "{\"status\":\"SUCCESS\",\"payload\":{\"positions\":[{\"trading_symbol\":\"NIFTY25SEP25000CE\",\"quantity\":75,\"product\":\"NRML\"}]}}"));
        server.start();

        var position = client.getPositionForTradingSymbol(USER_A, "NIFTY25SEP25000CE", "FNO");

        assertThat(position).isNotNull();
        assertThat(position.getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
    }

    @Test
    void getPositionForTradingSymbol_noMatch_returnsNull() throws IOException {
        tokenManager.storeToken(USER_A, "session-token", Instant.now().plusSeconds(3600));
        server.createContext("/v1/positions/trading-symbol", exchange -> sendJson(exchange, 200,
                "{\"status\":\"SUCCESS\",\"payload\":{\"positions\":[]}}"));
        server.start();

        var position = client.getPositionForTradingSymbol(USER_A, "NIFTY25SEP25000CE", "FNO");

        assertThat(position).isNull();
    }

    // ------------------------------------------------------------------
    // Multi-user isolation (spec "each Groww API call uses the calling user's own token")
    // ------------------------------------------------------------------

    @Test
    void getPositions_eachUserRequestCarriesThatUsersOwnBearerToken() throws IOException {
        tokenManager.storeToken(USER_A, "token-for-user-a", Instant.now().plusSeconds(3600));
        tokenManager.storeToken(USER_B, "token-for-user-b", Instant.now().plusSeconds(3600));
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        server.createContext("/v1/positions/user", exchange -> {
            capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, 200, "{\"status\":\"SUCCESS\",\"payload\":{\"positions\":[]}}");
        });
        server.start();

        client.getPositions(USER_A, "FNO");
        assertThat(capturedAuthHeader.get()).isEqualTo("Bearer token-for-user-a");

        client.getPositions(USER_B, "FNO");
        assertThat(capturedAuthHeader.get()).isEqualTo("Bearer token-for-user-b");
    }

    @Test
    void getPositions_userWithNoStoredToken_failsEvenWhileAnotherUserIsAuthenticated() {
        tokenManager.storeToken(USER_A, "token-for-user-a", Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> client.getPositions(USER_B, "FNO"))
                .isInstanceOf(GrowwApiException.class)
                .satisfies(ex -> assertThat(((GrowwApiException) ex).isAuthError()).isTrue());
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
