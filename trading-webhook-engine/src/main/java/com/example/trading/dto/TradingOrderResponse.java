package com.example.trading.dto;

import com.example.trading.entity.OrderEntity;
import com.example.trading.enums.TradingAction;
import com.example.trading.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** GET /api/trading/orders row - mirrors OrderEntity exactly (frontend's TradingOrder type). */
@Data
@Builder
public class TradingOrderResponse {
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

    public static TradingOrderResponse from(OrderEntity entity) {
        return TradingOrderResponse.builder()
                .id(entity.getId())
                .signalId(entity.getSignalId())
                .orderReferenceId(entity.getOrderReferenceId())
                .growwOrderId(entity.getGrowwOrderId())
                .underlying(entity.getUnderlying())
                .tradingSymbol(entity.getTradingSymbol())
                .action(entity.getAction())
                .quantity(entity.getQuantity())
                .price(entity.getPrice())
                .orderType(entity.getOrderType())
                .product(entity.getProduct())
                .segment(entity.getSegment())
                .status(entity.getStatus())
                .filledQuantity(entity.getFilledQuantity())
                .averageFillPrice(entity.getAverageFillPrice())
                .brokerRemark(entity.getBrokerRemark())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
