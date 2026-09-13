package com.example.trading.enums;

/**
 * Direction of a held position. Long option buys are always {@link #LONG};
 * {@link #SHORT} only ever occurs when {@code trading.allow-short-selling}
 * is enabled.
 */
public enum PositionSide {
    LONG,
    SHORT
}
