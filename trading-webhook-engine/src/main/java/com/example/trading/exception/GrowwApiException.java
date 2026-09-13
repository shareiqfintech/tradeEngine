package com.example.trading.exception;

import lombok.Getter;

/**
 * Wraps any failure talking to Groww: a {@code FAILURE} envelope, a non-2xx
 * HTTP status, a timeout, or a connection failure. {@code authError} is set
 * when the failure looks like an authentication/authorization problem
 * (HTTP 401/403, or a Groww error code meaning the token is invalid) so
 * callers can decide whether to invalidate the current token.
 */
@Getter
public class GrowwApiException extends RuntimeException {

    private final String growwErrorCode;
    private final boolean authError;

    public GrowwApiException(String message, String growwErrorCode, boolean authError) {
        super(message);
        this.growwErrorCode = growwErrorCode;
        this.authError = authError;
    }

    public GrowwApiException(String message, Throwable cause) {
        super(message, cause);
        this.growwErrorCode = null;
        this.authError = false;
    }

    public static GrowwApiException authError(String message, String growwErrorCode) {
        return new GrowwApiException(message, growwErrorCode, true);
    }

    public static GrowwApiException apiError(String message, String growwErrorCode) {
        return new GrowwApiException(message, growwErrorCode, false);
    }
}
