package com.example.trading.dto;

import com.example.trading.entity.PositionTargetEntity;
import com.example.trading.enums.PositionSide;
import com.example.trading.enums.TargetStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GET /api/trading/positions/targets - one automatic-profit-target row for
 * the calling user. Counts, prices and status only; never any Groww
 * credential/token.
 */
@Data
@Builder
public class PositionTargetResponse {
    private String tradingSymbol;
    private String underlying;
    private PositionSide side;
    private int quantity;
    /** ACTUAL Groww filled entry price; null until the fill is confirmed. */
    private BigDecimal entryPrice;
    /** User configuration snapshot (points, not a price). */
    private BigDecimal targetPoints;
    /** Backend-calculated: entryPrice + targetPoints (tick-normalized). Null until entryPrice is known. */
    private BigDecimal targetPrice;
    /** Last LTP the monitor observed for this exact contract. */
    private BigDecimal currentLtp;
    private TargetStatus targetStatus;
    private String exitOrderReferenceId;
    private BigDecimal ltpAtTrigger;
    private Instant updatedAt;

    public static PositionTargetResponse from(PositionTargetEntity e) {
        return PositionTargetResponse.builder()
                .tradingSymbol(e.getTradingSymbol())
                .underlying(e.getUnderlying())
                .side(e.getSide())
                .quantity(e.getQuantity())
                .entryPrice(e.getEntryPrice())
                .targetPoints(e.getTargetPoints())
                .targetPrice(e.getTargetPrice())
                .currentLtp(e.getLastLtp())
                .targetStatus(e.getTargetStatus())
                .exitOrderReferenceId(e.getExitOrderReferenceId())
                .ltpAtTrigger(e.getLtpAtTrigger())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
