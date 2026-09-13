package com.example.trading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A point-in-time copy of one broker position, captured whenever
 * {@code GrowwPositionService} queries Groww (before a SELL decision, and on
 * every reconciliation pass). This is a record, not a cache - the live
 * source of truth for "do we hold a long position" is always the broker.
 */
@Entity
@Table(name = "position_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class PositionSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_symbol", nullable = false, length = 100)
    private String tradingSymbol;

    @Column(name = "exchange", nullable = false, length = 10)
    private String exchange;

    @Column(name = "segment", nullable = false, length = 20)
    private String segment;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "net_quantity", nullable = false)
    private Integer netQuantity;

    @Column(name = "average_price", precision = 18, scale = 4)
    private BigDecimal averagePrice;

    @Column(name = "product", length = 20)
    private String product;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @PrePersist
    void onCreate() {
        this.capturedAt = Instant.now();
    }
}
