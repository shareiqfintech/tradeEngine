package com.example.trading.enums;

/**
 * Lifecycle of one {@code position_target} row - the automatic profit-target
 * exit for a single long option position.
 *
 * <pre>
 *   PENDING_ENTRY ──(actual fill price confirmed)──▶ MONITORING
 *                                                        │  LTP >= targetPrice
 *                                                        ▼
 *                                                   TARGET_HIT ──▶ CLOSING ──▶ CLOSED
 *                                                                     │
 *                                                                     └──▶ EXIT_FAILED (retried by the monitor)
 * </pre>
 *
 * <p>TARGET-ONLY: there is no stop-loss state.
 */
public enum TargetStatus {
    /** Entry order placed but Groww has not yet confirmed a valid filled price - no target computed, not monitoring. */
    PENDING_ENTRY,
    /** Target price computed from the actual fill; the poller is watching this exact option's LTP. */
    MONITORING,
    /** LTP reached the target price; exit is being initiated (transient). */
    TARGET_HIT,
    /** Exactly one SELL exit order has been submitted; awaiting confirmation. */
    CLOSING,
    /** Position closed - target achieved (or the position was already flat). */
    CLOSED,
    /** The exit order failed; the monitor will retry it. */
    EXIT_FAILED
}
