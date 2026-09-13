package com.example.trading.groww;

import com.example.trading.config.TradingProperties;
import com.example.trading.config.WebClientConfig;
import com.example.trading.exception.GrowwApiException;
import com.example.trading.groww.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The ONLY component that speaks HTTP to {@code api.groww.in}. Every other
 * service (auth, positions, orders, order-status, instrument master) goes
 * through the typed methods here, so there is exactly one place that builds
 * headers, handles the {@code {status, payload, error}} envelope, and turns
 * 2xx/4xx/5xx/timeout/connection failures into a single exception type
 * ({@link GrowwApiException}).
 *
 * <p>This class holds NO per-user mutable state itself (no {@code
 * accessToken} field) - every authenticated method below takes an explicit
 * {@code userId} and resolves that user's own token from {@link
 * GrowwTokenManager} fresh on every call, so nothing here can leak one
 * user's session into another user's request.
 */
@Slf4j
@Component
public class GrowwApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration ASSET_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient apiClient;
    private final WebClient assetClient;
    private final TradingProperties properties;
    private final GrowwTokenManager tokenManager;
    private final ObjectMapper objectMapper;

    public GrowwApiClient(@Qualifier(WebClientConfig.GROWW_API_CLIENT) WebClient apiClient,
                           @Qualifier(WebClientConfig.GROWW_ASSET_CLIENT) WebClient assetClient,
                           TradingProperties properties,
                           GrowwTokenManager tokenManager,
                           ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.assetClient = assetClient;
        this.properties = properties;
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Authentication - the one call that uses a user's API key, not a session token
    // ------------------------------------------------------------------

    /**
     * POST /v1/token/api/access - authenticates with the given user's own
     * API key + a TOTP code (already generated from that same user's TOTP
     * secret - see {@code GrowwAuthenticationService}). Unlike every other
     * Groww endpoint this client calls, the TOTP auth response is NOT
     * wrapped in the {status/payload/error} envelope - per Groww's docs
     * it's a flat {@code {token, tokenRefId, sessionName, expiry,
     * isActive}} object - so this deserializes {@link GrowwTokenResponse}
     * directly rather than going through {@link #unwrap}.
     */
    public GrowwTokenResponse requestAccessToken(String apiKey, String totpCode) {
        return apiClient.post()
                .uri("/v1/token/api/access")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GrowwTokenRequest.totp(totpCode))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(GrowwTokenResponse.class)
                .timeout(REQUEST_TIMEOUT)
                .onErrorMap(this::translateTransportError)
                .block();
    }

    // ------------------------------------------------------------------
    // Orders
    // ------------------------------------------------------------------

    /** POST /v1/order/create, using {@code userId}'s own session token. */
    public GrowwOrderResponse createOrder(Long userId, GrowwOrderRequest request) {
        return apiClient.post()
                .uri("/v1/order/create")
                .headers(headers -> addSessionHeaders(headers, userId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(new ParameterizedTypeReference<GrowwApiResponse<GrowwOrderResponse>>() {
                })
                .timeout(REQUEST_TIMEOUT)
                .map(this::unwrap)
                .onErrorMap(this::translateTransportError)
                .block();
    }

    /** GET /v1/order/status/{groww_order_id}?segment=, using {@code userId}'s own session token. */
    public GrowwOrderResponse getOrderStatus(Long userId, String growwOrderId, String segment) {
        return apiClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/order/status/{growwOrderId}")
                        .queryParam("segment", segment)
                        .build(growwOrderId))
                .headers(headers -> addSessionHeaders(headers, userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(new ParameterizedTypeReference<GrowwApiResponse<GrowwOrderResponse>>() {
                })
                .timeout(REQUEST_TIMEOUT)
                .map(this::unwrap)
                .onErrorMap(this::translateTransportError)
                .block();
    }

    /**
     * GET /v1/order/status/reference/{order_reference_id}?segment= - the
     * recovery path when a create-order call timed out on our side and we
     * must find out what actually happened before ever retrying. Uses
     * {@code userId}'s own session token.
     */
    public GrowwOrderResponse getOrderStatusByReference(Long userId, String orderReferenceId, String segment) {
        return apiClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/order/status/reference/{orderReferenceId}")
                        .queryParam("segment", segment)
                        .build(orderReferenceId))
                .headers(headers -> addSessionHeaders(headers, userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(new ParameterizedTypeReference<GrowwApiResponse<GrowwOrderResponse>>() {
                })
                .timeout(REQUEST_TIMEOUT)
                .map(this::unwrap)
                .onErrorMap(this::translateTransportError)
                .block();
    }

    // ------------------------------------------------------------------
    // Positions
    // ------------------------------------------------------------------

    /**
     * GET /v1/positions/user?segment=, using {@code userId}'s own session
     * token - Groww wraps the array as {@code payload: {"positions":
     * [...]}}, not a bare array (confirmed against the documented example
     * response), so this deserializes {@link GrowwPositionsPayload} and
     * unwraps the {@code positions} field, rather than binding {@code
     * payload} directly to a {@code List}.
     */
    public List<GrowwPositionDto> getPositions(Long userId, String segment) {
        GrowwPositionsPayload payload = apiClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/positions/user")
                        .queryParam("segment", segment)
                        .build())
                .headers(headers -> addSessionHeaders(headers, userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(new ParameterizedTypeReference<GrowwApiResponse<GrowwPositionsPayload>>() {
                })
                .timeout(REQUEST_TIMEOUT)
                .map(this::unwrap)
                .onErrorMap(this::translateTransportError)
                .block();
        return payload == null || payload.getPositions() == null ? List.of() : payload.getPositions();
    }

    /**
     * GET /v1/positions/trading-symbol?trading_symbol=&segment= - same
     * {@code {"positions": [...]}} payload shape as GET /v1/positions/user
     * (Groww returns the matching position(s), if any, in that array rather
     * than a single flat object). Uses {@code userId}'s own session token.
     */
    public GrowwPositionDto getPositionForTradingSymbol(Long userId, String tradingSymbol, String segment) {
        GrowwPositionsPayload payload = apiClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/positions/trading-symbol")
                        .queryParam("trading_symbol", tradingSymbol)
                        .queryParam("segment", segment)
                        .build())
                .headers(headers -> addSessionHeaders(headers, userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(new ParameterizedTypeReference<GrowwApiResponse<GrowwPositionsPayload>>() {
                })
                .timeout(REQUEST_TIMEOUT)
                .map(this::unwrap)
                .onErrorMap(this::translateTransportError)
                .block();
        if (payload == null || payload.getPositions() == null || payload.getPositions().isEmpty()) {
            return null;
        }
        return payload.getPositions().get(0);
    }

    // ------------------------------------------------------------------
    // Live data
    // ------------------------------------------------------------------

    /**
     * GET /v1/live-data/ltp?segment=&exchange_symbols= - last traded price
     * for one {@code EXCHANGE_SYMBOL} key (e.g. "NSE_NIFTY"). Convenience
     * wrapper over {@link #getLtps} for the single-symbol callers (spot
     * price for contract resolution/preview). Never a fabricated price.
     */
    public BigDecimal getLtp(Long userId, String exchange, String segment, String tradingSymbol) {
        String key = exchange + "_" + tradingSymbol;
        Map<String, BigDecimal> ltps = getLtps(userId, exchange, segment, List.of(tradingSymbol));
        return ltps == null ? null : ltps.get(key);
    }

    /**
     * Batched last-traded-price lookup: one {@code GET /v1/live-data/ltp}
     * call for many {@code tradingSymbols} at once, keyed by
     * {@code EXCHANGE_SYMBOL} (e.g. "NSE_NIFTY2691525000CE"). Used by the
     * target monitor so N monitored positions on the same contract cost
     * one request. Uses {@code userId}'s own session token. Returns an
     * empty map (never null) if Groww returns nothing.
     */
    public Map<String, BigDecimal> getLtps(Long userId, String exchange, String segment, List<String> tradingSymbols) {
        if (tradingSymbols == null || tradingSymbols.isEmpty()) {
            return Map.of();
        }
        String exchangeSymbols = tradingSymbols.stream()
                .distinct()
                .map(symbol -> exchange + "_" + symbol)
                .reduce((a, b) -> a + "," + b)
                .orElseThrow();
        Map<String, BigDecimal> payload = apiClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/live-data/ltp")
                        .queryParam("segment", segment)
                        .queryParam("exchange_symbols", exchangeSymbols)
                        .build())
                .headers(headers -> addSessionHeaders(headers, userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(new ParameterizedTypeReference<GrowwApiResponse<Map<String, BigDecimal>>>() {
                })
                .timeout(REQUEST_TIMEOUT)
                .map(this::unwrap)
                .onErrorMap(this::translateTransportError)
                .block();
        return payload == null ? Map.of() : payload;
    }

    // ------------------------------------------------------------------
    // Instrument master (public CSV asset, no auth headers, no user)
    // ------------------------------------------------------------------

    public String fetchInstrumentMasterCsv() {
        return assetClient.get()
                .uri(properties.getGroww().getInstrumentCsvUrl())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        Mono.error(GrowwApiException.apiError("Failed to download Groww instrument master: HTTP "
                                + response.statusCode().value(), null)))
                .bodyToMono(String.class)
                .timeout(ASSET_TIMEOUT)
                .onErrorMap(this::translateTransportError)
                .block();
    }

    // ------------------------------------------------------------------
    // Shared plumbing
    // ------------------------------------------------------------------

    /** Every non-auth API call carries the bearer session token (resolved fresh for THIS userId) plus X-API-VERSION. */
    private void addSessionHeaders(HttpHeaders headers, Long userId) {
        String token = tokenManager.getToken(userId)
                .orElseThrow(() -> GrowwApiException.authError("No valid Groww access token available", "NO_TOKEN"));
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        headers.set("X-API-VERSION", properties.getGroww().getApiVersion());
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    private <T> T unwrap(GrowwApiResponse<T> response) {
        if (response == null) {
            throw GrowwApiException.apiError("Empty response from Groww", null);
        }
        if (!response.isSuccess()) {
            String code = response.getError() != null ? response.getError().getCode() : null;
            String message = response.getError() != null ? response.getError().getMessage() : "Groww returned FAILURE";
            throw GrowwApiException.apiError(message, code);
        }
        return response.getPayload();
    }

    private Mono<Throwable> toApiException(ClientResponse response) {
        boolean authError = response.statusCode().value() == 401 || response.statusCode().value() == 403;
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(rawBody -> {
                    // Logging the raw body is safe here: this is Groww's OWN error
                    // response describing why it rejected the request (e.g. "invalid
                    // api key format"), never our request credentials.
                    log.warn("GROWW_API_ERROR_BODY status={} body={}", response.statusCode().value(), rawBody);

                    GrowwApiResponse<?> parsed = parseErrorBody(rawBody);
                    String code = parsed != null && parsed.getError() != null ? parsed.getError().getCode() : null;
                    String message = parsed != null && parsed.getError() != null && parsed.getError().getMessage() != null
                            ? parsed.getError().getMessage()
                            : (!rawBody.isBlank() ? rawBody : "Groww API returned HTTP " + response.statusCode().value());
                    log.warn("GROWW_API_ERROR status={} code={} authError={}", response.statusCode().value(), code, authError);
                    return new GrowwApiException(message, code, authError);
                });
    }

    private GrowwApiResponse<?> parseErrorBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, GrowwApiResponse.class);
        } catch (Exception ex) {
            log.debug("GROWW_API_ERROR_BODY_UNPARSEABLE reason={}", ex.getMessage());
            return null;
        }
    }

    private Throwable translateTransportError(Throwable throwable) {
        if (throwable instanceof GrowwApiException) {
            return throwable;
        }
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return new GrowwApiException("Timed out calling Groww API", throwable);
        }
        if (throwable instanceof WebClientRequestException) {
            return new GrowwApiException("Failed to connect to Groww API", throwable);
        }
        return new GrowwApiException("Unexpected error calling Groww API", throwable);
    }
}
