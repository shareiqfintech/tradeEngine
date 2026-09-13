package com.example.trading.exception;

/** Thrown when Groww (or, in PAPER mode, our own simulation) rejects an order outright. */
public class OrderRejectedException extends RuntimeException {
    public OrderRejectedException(String message) {
        super(message);
    }
}
