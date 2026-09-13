package com.example.trading.exception;

/** Thrown when a signalId has already been seen (Redis {@code trading:signal:{signalId}} key present). */
public class DuplicateSignalException extends RuntimeException {
    public DuplicateSignalException(String signalId) {
        super("Duplicate signal: " + signalId);
    }
}
