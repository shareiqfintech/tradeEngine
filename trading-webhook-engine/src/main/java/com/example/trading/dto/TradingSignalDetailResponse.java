package com.example.trading.dto;

import com.example.trading.enums.SignalStatus;
import com.example.trading.enums.TradingAction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/trading/signals/{signalId} - the signal plus its audit trail and
 * resulting order, if any. Deliberately a flat, standalone DTO (not
 * extending TradingSignalResponse) to avoid Lombok @Builder/inheritance
 * pitfalls - the field duplication is a small, safe price for that.
 */
@Data
@Builder
public class TradingSignalDetailResponse {
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
    private List<AuditEventResponse> auditTrail;
    private TradingOrderResponse order;

    public static TradingSignalDetailResponse from(TradingSignalResponse base, List<AuditEventResponse> auditTrail,
                                                     TradingOrderResponse order) {
        return TradingSignalDetailResponse.builder()
                .id(base.getId())
                .signalId(base.getSignalId())
                .action(base.getAction())
                .underlying(base.getUnderlying())
                .exchange(base.getExchange())
                .timeframe(base.getTimeframe())
                .price(base.getPrice())
                .signalTimestamp(base.getSignalTimestamp())
                .status(base.getStatus())
                .rejectionReason(base.getRejectionReason())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .auditTrail(auditTrail)
                .order(order)
                .build();
    }
}
