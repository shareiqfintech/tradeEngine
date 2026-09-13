package com.example.trading.enums;

/**
 * PAPER (default, safe): the engine runs every check but simulates the order
 * instead of calling Groww's order/create endpoint.
 * LIVE: the engine submits real orders to Groww. Only reachable via explicit
 * configuration ({@code trading.mode=LIVE}) - never a runtime toggle.
 */
public enum TradingMode {
    PAPER,
    LIVE
}
