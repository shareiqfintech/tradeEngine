package com.example.trading.exception;

import java.math.BigDecimal;

/**
 * The submitted F&O Target Points value is missing, not positive, or
 * outside the configured {@code trading.exit.target.min-points /
 * max-points} range. Mapped to HTTP 422 by
 * {@link GlobalExceptionHandler}; the configuration is never saved.
 */
public class InvalidTargetPointsException extends RuntimeException {

    public InvalidTargetPointsException(String message) {
        super(message);
    }

    public static InvalidTargetPointsException outOfRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return new InvalidTargetPointsException(
                "targetPoints " + value + " is invalid - must be > 0 and within [" + min + ", " + max + "]");
    }
}
