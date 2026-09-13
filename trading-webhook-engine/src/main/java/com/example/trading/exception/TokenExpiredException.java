package com.example.trading.exception;

/** Thrown when Groww rejects a call with 401/403 mid-flow, i.e. the token expired after we last checked it. */
public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String message) {
        super(message);
    }
}
