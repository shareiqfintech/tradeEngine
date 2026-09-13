package com.example.trading.entity;

import com.example.trading.enums.SignalStatus;
import com.example.trading.enums.TradingAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trading_signal")
@Getter
@Setter
@NoArgsConstructor
public class TradingSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false, unique = true, length = 100)
    private String signalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10)
    private TradingAction action;

    @Column(name = "underlying", nullable = false, length = 20)
    private String underlying;

    @Column(name = "exchange", nullable = false, length = 10)
    private String exchange;

    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    @Column(name = "price", nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "signal_timestamp", nullable = false)
    private Instant signalTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SignalStatus status;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

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

    public static TradingSignalEntity received(String signalId, TradingAction action, String underlying,
                                                String exchange, String timeframe, BigDecimal price,
                                                Instant signalTimestamp) {
        TradingSignalEntity entity = new TradingSignalEntity();
        entity.setSignalId(signalId);
        entity.setAction(action);
        entity.setUnderlying(underlying);
        entity.setExchange(exchange);
        entity.setTimeframe(timeframe);
        entity.setPrice(price);
        entity.setSignalTimestamp(signalTimestamp);
        entity.setStatus(SignalStatus.RECEIVED);
        return entity;
    }
}
