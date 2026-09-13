package com.example.trading.entity;

import com.example.trading.enums.OrderStatus;
import com.example.trading.enums.TradingAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false, length = 100)
    private String signalId;

    /** Which application user's own Groww account this order was placed on behalf of - see GrowwUserResolver. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "order_reference_id", nullable = false, unique = true, length = 40)
    private String orderReferenceId;

    @Column(name = "groww_order_id", length = 100)
    private String growwOrderId;

    @Column(name = "underlying", nullable = false, length = 20)
    private String underlying;

    @Column(name = "trading_symbol", nullable = false, length = 100)
    private String tradingSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10)
    private TradingAction action;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price", precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    @Column(name = "product", nullable = false, length = 20)
    private String product;

    @Column(name = "segment", nullable = false, length = 20)
    private String segment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "filled_quantity")
    private Integer filledQuantity = 0;

    @Column(name = "average_fill_price", precision = 18, scale = 4)
    private BigDecimal averageFillPrice;

    @Column(name = "broker_remark", length = 500)
    private String brokerRemark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.filledQuantity == null) {
            this.filledQuantity = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
