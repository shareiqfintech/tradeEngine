package com.example.trading.exception;

/** Thrown when a signal arrives outside 09:15-15:30 IST (or on a non-trading day). */
public class MarketClosedException extends RuntimeException {
    public MarketClosedException(String message) {
        super(message);
    }
}
