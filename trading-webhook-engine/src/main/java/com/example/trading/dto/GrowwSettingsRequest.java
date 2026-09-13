package com.example.trading.dto;

import lombok.Data;

/**
 * PUT /api/settings/groww request. Either field being {@code null} or blank
 * means "leave the currently-saved value unchanged" - the frontend never
 * sends back the masked placeholder it displays; it only includes a field
 * here when the user actually typed a new value into it.
 */
@Data
public class GrowwSettingsRequest {
    private String apiKey;
    private String totpSecret;
}
