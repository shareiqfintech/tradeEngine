package com.example.trading.enums;

/**
 * Lifecycle of an {@code orders} row. {@link #PENDING} is a local-only state
 * used between "order reference generated" and "broker accepted/rejected it"
 * (including the paper-mode simulation path). The remaining values mirror
 * Groww's {@code order_status} values exactly, per the Groww Trade API
 * order-status/order-detail endpoints.
 */
public enum OrderStatus {
    PENDING,
    OPEN,
    COMPLETE,
    REJECTED,
    CANCELLED,
    FAILED
}
