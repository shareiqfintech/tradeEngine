package com.example.trading.dto;

import com.example.trading.enums.GrowwAuthState;

/**
 * Response for the manual "authenticate now" trigger
 * ({@code POST /api/trading/groww/authenticate}). Never contains the
 * access token, API key, or TOTP secret - only the resulting state.
 */
public record GrowwAuthCheckResponse(boolean authenticated, GrowwAuthState state) {
}
