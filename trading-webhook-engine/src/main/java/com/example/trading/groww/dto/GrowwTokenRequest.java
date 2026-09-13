package com.example.trading.groww.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of {@code POST /v1/token/api/access} for the TOTP authentication flow. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrowwTokenRequest {

    private String keyType;
    private String totp;

    public static GrowwTokenRequest totp(String totpCode) {
        return new GrowwTokenRequest("totp", totpCode);
    }
}
