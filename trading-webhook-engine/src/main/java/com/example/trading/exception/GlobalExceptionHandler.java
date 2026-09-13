package com.example.trading.exception;

import com.example.trading.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central error handling for the webhook and (in later phases) admin APIs.
 *
 * <p>Every handler here returns a clean {@link ErrorResponse} - no stack
 * traces, no secrets, no internal exception messages leaked to the caller.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Bean-validation failures on the request body (e.g. missing/invalid fields). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("SIGNAL_VALIDATION_FAILED path={} errors={}", request.getRequestURI(), fieldErrors);
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST, "Signal validation failed",
                request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Malformed JSON, or JSON that does not match the DTO (e.g. an unknown action value). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest request) {
        log.warn("INVALID_JSON path={} reason={}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST,
                "Malformed or invalid JSON payload", request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /** Defensive: covers any code path that throws this outside the auth filter. */
    @ExceptionHandler(InvalidWebhookSecretException.class)
    public ResponseEntity<ErrorResponse> handleAuth(InvalidWebhookSecretException ex,
                                                     HttpServletRequest request) {
        log.warn("WEBHOOK_AUTH_FAILED path={}", request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED,
                "Invalid or missing webhook secret", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(InvalidSignalException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSignal(InvalidSignalException ex, HttpServletRequest request) {
        log.warn("INVALID_SIGNAL path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateSignalException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSignal(DuplicateSignalException ex, HttpServletRequest request) {
        log.warn("DUPLICATE_SIGNAL path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MarketClosedException.class)
    public ResponseEntity<ErrorResponse> handleMarketClosed(MarketClosedException ex, HttpServletRequest request) {
        log.warn("MARKET_CLOSED path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Safety net - a new entry attempted outside the 09:25-15:10 IST window.
     * Normally this is caught inside the async engine and recorded on the
     * signal; it only reaches here if thrown on a request thread.
     */
    @ExceptionHandler(TradingSessionClosedException.class)
    public ResponseEntity<ErrorResponse> handleTradingSessionClosed(TradingSessionClosedException ex, HttpServletRequest request) {
        log.warn("TRADING_SESSION_CLOSED path={} reasonCode={} reason={}", request.getRequestURI(), ex.getReasonCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getReasonCode() + ": " + ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAuthRequired(AuthenticationRequiredException ex, HttpServletRequest request) {
        log.warn("AUTHENTICATION_REQUIRED path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException ex, HttpServletRequest request) {
        log.warn("TOKEN_EXPIRED path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(RiskRejectedException.class)
    public ResponseEntity<ErrorResponse> handleRiskRejected(RiskRejectedException ex, HttpServletRequest request) {
        log.warn("RISK_REJECTED path={} reasonCode={} reason={}", request.getRequestURI(), ex.getReasonCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(PositionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePositionNotFound(PositionNotFoundException ex, HttpServletRequest request) {
        log.warn("POSITION_NOT_FOUND path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(PositionCloseIncompleteException.class)
    public ResponseEntity<ErrorResponse> handlePositionCloseIncomplete(PositionCloseIncompleteException ex, HttpServletRequest request) {
        log.warn("POSITION_CLOSE_INCOMPLETE path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ContractNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleContractNotFound(ContractNotFoundException ex, HttpServletRequest request) {
        log.warn("CONTRACT_NOT_FOUND path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidLotSizeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLotSize(InvalidLotSizeException ex, HttpServletRequest request) {
        log.warn("INVALID_LOT_SIZE path={} requested={} valid={}", request.getRequestURI(), ex.getRequestedLotSize(), ex.getValidLotSizes());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidTargetPointsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTargetPoints(InvalidTargetPointsException ex, HttpServletRequest request) {
        log.warn("INVALID_TARGET_POINTS path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(GrowwApiException.class)
    public ResponseEntity<ErrorResponse> handleGrowwApi(GrowwApiException ex, HttpServletRequest request) {
        // The trailing throwable arg is required for SLF4J to print the stack
        // trace/cause chain - without it, the real underlying cause (e.g. a
        // response-deserialization failure) was never visible in any log.
        log.error("GROWW_API_EXCEPTION path={} code={} authError={} reason={}",
                request.getRequestURI(), ex.getGrowwErrorCode(), ex.isAuthError(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.of(HttpStatus.BAD_GATEWAY, "Groww API error: " + ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(OrderRejectedException.class)
    public ResponseEntity<ErrorResponse> handleOrderRejected(OrderRejectedException ex, HttpServletRequest request) {
        log.warn("ORDER_REJECTED path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
        log.warn("DUPLICATE_EMAIL path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
    }

    /** Deliberately logs no detail about which credential was wrong - see the exception's own javadoc. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("SIGN_IN_FAILED path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(GrowwConfigurationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGrowwConfigurationNotFound(GrowwConfigurationNotFoundException ex, HttpServletRequest request) {
        log.warn("GROWW_CONFIGURATION_NOT_FOUND path={} reason={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI()));
    }

    /** Thrown directly by read-only query endpoints (e.g. TradingQueryController) for a missing signal/order id. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        log.warn("RESPONSE_STATUS_EXCEPTION path={} status={} reason={}", request.getRequestURI(), status, ex.getReason());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("UNEXPECTED_ERROR path={}", request.getRequestURI(), ex);
        ErrorResponse body = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error", request.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }
}
