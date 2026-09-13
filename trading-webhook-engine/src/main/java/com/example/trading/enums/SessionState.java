package com.example.trading.enums;

/**
 * The trading-session lifecycle for one IST trading day.
 *
 * <p>The first five values form the time-driven state machine advanced by
 * {@code TradingSessionService} / {@code TradingWindowScheduler}:
 *
 * <pre>
 *   PRE_MARKET ──09:25──▶ TRADING_ACTIVE ──15:10──▶ SESSION_CLOSING
 *        ▲                                                  │
 *        │                             (all system-managed positions closed &amp; reconciled)
 *        │                                                  ▼
 *        └────────── next trading day ────── MARKET_CLOSED ◀──15:30── SAFETY_EXIT_COMPLETED
 * </pre>
 *
 * <p><b>15:10 is the application's SAFETY EXIT / trading cutoff - NOT the
 * official market close.</b> 15:30 is the official NSE close. New positions
 * must never be created at or after 15:10.
 *
 * <p>{@link #PAUSED}, {@link #KILL_SWITCH} and {@link #AUTH_REQUIRED} are
 * not lifecycle states - they are higher-priority display overlays the
 * status API reports when the corresponding flag is set (mirroring the
 * frontend's existing {@code deriveTradingStatusLabel} priority).
 */
public enum SessionState {
    PRE_MARKET,
    TRADING_ACTIVE,
    SESSION_CLOSING,
    SAFETY_EXIT_COMPLETED,
    MARKET_CLOSED,
    PAUSED,
    KILL_SWITCH,
    AUTH_REQUIRED
}
