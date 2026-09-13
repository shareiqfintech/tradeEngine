package com.example.trading.exception;

/** Thrown when an order-affecting operation is attempted without a usable Groww access token. */
public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
