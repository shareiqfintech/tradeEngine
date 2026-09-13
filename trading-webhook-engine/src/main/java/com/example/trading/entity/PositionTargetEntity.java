package com.example.trading.entity;

import com.example.trading.enums.PositionSide;
import com.example.trading.enums.TargetStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The automatic profit-target for one long option position, from
 * "entry order placed" to "closed at target".
 *
 * <p>{@code targetPoints} and {@code targetPrice} are SNAPSHOTTED here when
 * the position opens - a later change to the user's F&O configuration only
 * affects NEW positions, never this one. {@code targetPrice} stays null
 * until Groww confirms a real {@code entryPrice} (see section 18 - never
 * calculated from the TradingView signal price).
 *
 * <p>All monitor state lives in this row so the single
 * {@code TargetMonitorScheduler} resumes cleanly after a restart. Exactly
 * one exit order is ever submitted, guarded by {@link #version} optimistic
 * locking + a {@code trading:target-exit:{id}} distributed lock + the
 * deterministic {@link #exitOrderReferenceId}.
 */
@Entity
@Table(name = "position_target",
        uniqueConstraints = @UniqueConstraint(name = "uq_position_target_entry_order", columnNames = "entry_order_reference_id"))
@Getter
@Setter
@NoArgsConstructor
public class PositionTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "signal_id", nullable = false, length = 100)
    private String signalId;

    @Column(name = "entry_order_reference_id", nullable = false, length = 40)
    private String entryOrderReferenceId;

    @Column(name = "groww_entry_order_id", length = 100)
    private String growwEntryOrderId;

    @Column(name = "trading_symbol", nullable = false, length = 100)
    private String tradingSymbol;

    @Column(name = "exchange", nullable = false, length = 20)
    private String exchange;

    @Column(name = "segment", nullable = false, length = 20)
    private String segment;

    @Column(name = "underlying", nullable = false, length = 20)
    private String underlying;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private PositionSide side = PositionSide.LONG;

    /** Entry (and default exit) quantity. Actual exit quantity is re-read from the broker at exit time. */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** The ACTUAL filled entry price from Groww. Null until confirmed - no target is computed before then. */
    @Column(name = "entry_price", precision = 18, scale = 4)
    private BigDecimal entryPrice;

    /** Snapshot of the user's Target Points at the moment this position opened. */
    @Column(name = "target_points", nullable = false, precision = 18, scale = 4)
    private BigDecimal targetPoints;

    @Column(name = "target_enabled", nullable = false)
    private boolean targetEnabled = true;

    /** entryPrice + targetPoints, tick-normalized. Null until {@link #entryPrice} is known. */
    @Column(name = "target_price", precision = 18, scale = 4)
    private BigDecimal targetPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_status", nullable = false, length = 20)
    private TargetStatus targetStatus;

    @Column(name = "last_ltp", precision = 18, scale = 4)
    private BigDecimal lastLtp;

    @Column(name = "last_ltp_at")
    private Instant lastLtpAt;

    /** The LTP reading that crossed the target and triggered the exit. */
    @Column(name = "ltp_at_trigger", precision = 18, scale = 4)
    private BigDecimal ltpAtTrigger;

    @Column(name = "exit_order_reference_id", length = 40)
    private String exitOrderReferenceId;

    @Column(name = "groww_exit_order_id", length = 100)
    private String growwExitOrderId;

    @Column(name = "exited_quantity")
    private Integer exitedQuantity;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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
}
