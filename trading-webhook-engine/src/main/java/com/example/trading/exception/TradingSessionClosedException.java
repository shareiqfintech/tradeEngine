package com.example.trading.exception;

/**
 * Thrown by {@code TradingSessionService.assertNewEntryAllowed()} when a NEW
 * entry order must be rejected because of where "now" (Asia/Kolkata) falls
 * relative to the strategy trading window:
 *
 * <ul>
 *   <li>{@code SESSION_NOT_STARTED}   - before 09:25 IST, or a non-trading day</li>
 *   <li>{@code SESSION_TRADING_CUTOFF}- at/after 15:10 IST (the safety exit), before 15:30</li>
 *   <li>{@code MARKET_CLOSED}         - at/after 15:30 IST (official close)</li>
 * </ul>
 *
 * <p>This is the guard that runs immediately before every Groww
 * create-order for a new entry (and before the PAPER simulation), so a
 * signal delayed in the async queue past 15:10 still never opens a
 * position. Exit / position-close orders are never blocked by it.
 */
public class TradingSessionClosedException extends RuntimeException {

    public static final String SESSION_NOT_STARTED = "SESSION_NOT_STARTED";
    public static final String SESSION_TRADING_CUTOFF = "SESSION_TRADING_CUTOFF";
    public static final String MARKET_CLOSED = "MARKET_CLOSED";

    private final String reasonCode;

    public TradingSessionClosedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
