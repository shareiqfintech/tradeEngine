package com.example.trading.dto;

import com.example.trading.enums.TradingAction;
import com.example.trading.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** GET /api/trading/orders/{id} - the order plus its audit trail and originating signal, if found. */
@Data
@Builder
public class TradingOrderDetailResponse {
    private Long id;
    private String signalId;
    private String orderReferenceId;
    private String growwOrderId;
    private String underlying;
    private String tradingSymbol;
    private TradingAction action;
    private Integer quantity;
    private BigDecimal price;
    private String orderType;
    private String product;
    private String segment;
    private OrderStatus status;
    private Integer filledQuantity;
    private BigDecimal averageFillPrice;
    private String brokerRemark;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEventResponse> auditTrail;
    private TradingSignalResponse signal;

    public static TradingOrderDetailResponse from(TradingOrderResponse base, List<AuditEventResponse> auditTrail,
                                                    TradingSignalResponse signal) {
        return TradingOrderDetailResponse.builder()
                .id(base.getId())
                .signalId(base.getSignalId())
                .orderReferenceId(base.getOrderReferenceId())
                .growwOrderId(base.getGrowwOrderId())
                .underlying(base.getUnderlying())
                .tradingSymbol(base.getTradingSymbol())
                .action(base.getAction())
                .quantity(base.getQuantity())
                .price(base.getPrice())
                .orderType(base.getOrderType())
                .product(base.getProduct())
                .segment(base.getSegment())
                .status(base.getStatus())
                .filledQuantity(base.getFilledQuantity())
                .averageFillPrice(base.getAverageFillPrice())
                .brokerRemark(base.getBrokerRemark())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .auditTrail(auditTrail)
                .signal(signal)
                .build();
    }
}
