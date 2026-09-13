package com.example.trading.exception;

/**
 * Thrown when the SELL used to close an existing opposite-side option
 * position (as part of signal-driven CE/PE switching) does not fully close
 * it. The new same-direction BUY must never proceed when this is thrown.
 */
public class PositionCloseIncompleteException extends RuntimeException {
    public PositionCloseIncompleteException(String message) {
        super(message);
    }
}
