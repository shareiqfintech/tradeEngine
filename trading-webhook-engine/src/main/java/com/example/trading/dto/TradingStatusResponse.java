package com.example.trading.dto;

import com.example.trading.enums.TradingMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/trading/status} response. Deliberately contains nothing
 * more sensitive than counts and booleans - never the Groww API key, TOTP
 * secret, or access token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingStatusResponse {
    private TradingMode mode;
    /** True only when a brand-new entry may be opened right now (09:25-15:10 IST, no kill switch / pause). */
    private boolean tradingEnabled;
    private boolean growwAuthenticated;
    private boolean killSwitch;
    private boolean paused;
    private int ordersToday;
    private int openPositions;

    /** PRE_MARKET | TRADING_ACTIVE | SESSION_CLOSING | SAFETY_EXIT_COMPLETED | MARKET_CLOSED (or KILL_SWITCH / PAUSED / AUTH_REQUIRED overlay). */
    private String sessionState;
    /** "09:25" - strategy session start (IST). */
    private String sessionStart;
    /** "15:10" - SAFETY EXIT / new-entry cutoff (IST). NOT the market close. */
    private String tradingCutoff;
    /** "15:30" - official NSE close (IST). */
    private String officialMarketClose;
    /** "Asia/Kolkata". */
    private String timezone;
}
