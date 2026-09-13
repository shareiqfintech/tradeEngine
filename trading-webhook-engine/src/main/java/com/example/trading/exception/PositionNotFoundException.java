package com.example.trading.exception;

/** Thrown for a SELL signal when no matching open LONG position exists at the broker (reason: NO_LONG_POSITION). */
public class PositionNotFoundException extends RuntimeException {
    public PositionNotFoundException(String message) {
        super(message);
    }
}
