package com.example.trading.dto;

import lombok.Builder;
import lombok.Data;

/**
 * GET/PUT /api/settings/groww response. Never carries the full API key or
 * TOTP secret - {@code maskedApiKey} shows only the last 4 characters, and
 * the TOTP secret is never echoed back at all (not even masked), matching
 * the "do not return secrets unnecessarily" requirement.
 */
@Data
@Builder
public class GrowwSettingsResponse {
    private boolean configured;
    private boolean apiKeyConfigured;
    private boolean totpConfigured;
    /** True only after a real, successful Test Connection call - see GrowwConfigurationEntity#connected. */
    private boolean connected;
    private String maskedApiKey;
}
