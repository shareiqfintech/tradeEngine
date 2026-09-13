package com.example.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /api/settings/groww/test-connection response. {@code message} is always a fixed, safe string - never a raw Groww error or credential. */
@Data
@AllArgsConstructor
public class GrowwConnectionTestResponse {
    private boolean connected;
    private String message;
}
