package com.example.trading.exception;

import lombok.Getter;

/** Thrown when {@code RiskManagementService} returns a REJECTED decision. */
@Getter
public class RiskRejectedException extends RuntimeException {

    private final String reasonCode;

    public RiskRejectedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }
}
