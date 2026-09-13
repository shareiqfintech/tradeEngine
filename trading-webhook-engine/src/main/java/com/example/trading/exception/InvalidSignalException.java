package com.example.trading.exception;

/** Thrown when a signal fails business-rule validation beyond bean validation (e.g. unknown underlying). */
public class InvalidSignalException extends RuntimeException {
    public InvalidSignalException(String message) {
        super(message);
    }
}
