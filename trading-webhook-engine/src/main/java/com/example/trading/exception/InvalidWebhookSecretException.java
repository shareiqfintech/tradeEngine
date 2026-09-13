package com.example.trading.exception;

/**
 * Thrown when a request to a TradingView webhook endpoint is missing the
 * {@code X-TradingView-Secret} header, presents the wrong value, or the
 * server itself has no secret configured. The exception message must never
 * contain the secret value or the header's contents.
 */
public class InvalidWebhookSecretException extends RuntimeException {

    public InvalidWebhookSecretException(String message) {
        super(message);
    }
}
