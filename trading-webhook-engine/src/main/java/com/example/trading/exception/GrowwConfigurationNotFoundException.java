package com.example.trading.exception;

/** Thrown when a user attempts a Groww connection test before saving both an API key and a TOTP configuration. */
public class GrowwConfigurationNotFoundException extends RuntimeException {
    public GrowwConfigurationNotFoundException(String message) {
        super(message);
    }
}
