package com.example.trading.dto;

import com.example.trading.entity.TradingSignalEntity;
import com.example.trading.enums.SignalStatus;
import com.example.trading.enums.TradingAction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** GET /api/trading/signals row - mirrors TradingSignalEntity exactly (frontend's TradingSignal type). */
@Data
@Builder
public class TradingSignalResponse {
    private Long id;
    private String signalId;
    private TradingAction action;
    private String underlying;
    private String exchange;
    private String timeframe;
    private BigDecimal price;
    private Instant signalTimestamp;
    private SignalStatus status;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;

    public static TradingSignalResponse from(TradingSignalEntity entity) {
        return TradingSignalResponse.builder()
                .id(entity.getId())
                .signalId(entity.getSignalId())
                .action(entity.getAction())
                .underlying(entity.getUnderlying())
                .exchange(entity.getExchange())
                .timeframe(entity.getTimeframe())
                .price(entity.getPrice())
                .signalTimestamp(entity.getSignalTimestamp())
                .status(entity.getStatus())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
