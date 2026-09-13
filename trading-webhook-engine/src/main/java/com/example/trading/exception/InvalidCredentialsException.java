package com.example.trading.exception;

/** Thrown at sign-in for either an unknown email or a wrong password - deliberately the same exception/message for both, so a failed login never reveals whether the email exists. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
