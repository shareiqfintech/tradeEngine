package com.example.trading.dto;

import com.example.trading.enums.TradingAction;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload sent by TradingView to {@code POST /api/webhook/tradingview}.
 *
 * <pre>{@code
 * {
 *   "signalId": "NIFTY-15M-001",
 *   "action": "BUY",
 *   "underlying": "NIFTY",
 *   "exchange": "NSE",
 *   "timeframe": "15m",
 *   "price": 25000.50,
 *   "timestamp": "2026-09-08T09:30:00+05:30"
 * }
 * }</pre>
 *
 * <p>This DTO intentionally carries no broker credentials or tokens - see the
 * project's webhook security design notes. It only describes the trade
 * intent; everything else (authentication, risk checks, contract resolution,
 * order placement) is decided server-side in later phases.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingViewSignal implements Serializable {

    @NotBlank(message = "signalId is required")
    private String signalId;

    @NotNull(message = "action is required and must be BUY or SELL")
    private TradingAction action;

    @NotBlank(message = "underlying is required")
    private String underlying;

    @NotBlank(message = "exchange is required")
    private String exchange;

    @NotBlank(message = "timeframe is required")
    private String timeframe;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "timestamp is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
}
