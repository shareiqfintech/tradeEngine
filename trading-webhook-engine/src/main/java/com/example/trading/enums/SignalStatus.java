package com.example.trading.enums;

/**
 * Lifecycle of a {@code trading_signal} row, from the moment TradingView's
 * webhook is received to the terminal outcome of processing it. One signal
 * is fanned out to every connected Groww user (see
 * {@code TradingEngineService}/{@code GrowwUserResolver}); EXECUTED/REJECTED/
 * FAILED here reflect the aggregate across all of them (EXECUTED = every
 * connected user succeeded, REJECTED/FAILED = none did), while
 * PARTIALLY_EXECUTED covers the case where some connected users succeeded
 * and others did not. The individual result for each user is recorded
 * separately - see {@code SignalExecutionEntity}.
 */
public enum SignalStatus {
    RECEIVED,
    VALIDATED,
    PROCESSING,
    EXECUTED,
    PARTIALLY_EXECUTED,
    REJECTED,
    DUPLICATE,
    FAILED
}
