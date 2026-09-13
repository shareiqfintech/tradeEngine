package com.example.trading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_trading_summary")
@Getter
@Setter
@NoArgsConstructor
public class DailyTradingSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_date", nullable = false, unique = true)
    private LocalDate tradingDate;

    @Column(name = "orders_count", nullable = false)
    private Integer ordersCount = 0;

    @Column(name = "buy_count", nullable = false)
    private Integer buyCount = 0;

    @Column(name = "sell_count", nullable = false)
    private Integer sellCount = 0;

    @Column(name = "rejected_count", nullable = false)
    private Integer rejectedCount = 0;

    @Column(name = "realized_pnl", precision = 18, scale = 4)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static DailyTradingSummaryEntity forDate(LocalDate date) {
        DailyTradingSummaryEntity entity = new DailyTradingSummaryEntity();
        entity.setTradingDate(date);
        return entity;
    }
}
