package com.example.trading.exception;

/** Thrown at sign-up when the email is already registered. */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
