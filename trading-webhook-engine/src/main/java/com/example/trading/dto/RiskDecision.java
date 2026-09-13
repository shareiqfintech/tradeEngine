package com.example.trading.dto;

import lombok.Builder;
import lombok.Data;

/** Outcome of {@code RiskManagementService}'s checks for one signal. */
@Data
@Builder
public class RiskDecision {
    private boolean approved;
    private String reasonCode;
    private String reason;

    public static RiskDecision approved() {
        return RiskDecision.builder().approved(true).build();
    }

    public static RiskDecision rejected(String reasonCode, String reason) {
        return RiskDecision.builder().approved(false).reasonCode(reasonCode).reason(reason).build();
    }
}
