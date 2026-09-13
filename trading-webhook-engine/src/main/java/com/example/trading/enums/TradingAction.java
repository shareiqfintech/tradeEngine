package com.example.trading.enums;

/**
 * The trading action requested by an incoming TradingView signal.
 *
 * <p>IMPORTANT (see project README / spec): a {@link #SELL} signal does NOT
 * mean "open a new short position". In this system it means "close an
 * existing long position". Opening a short position requires the
 * {@code trading.allow-short-selling} configuration flag to be explicitly
 * enabled (implemented in a later phase, alongside the risk engine).
 */
public enum TradingAction {
    BUY,
    SELL
}
