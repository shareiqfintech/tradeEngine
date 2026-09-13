package com.example.trading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-user, per-underlying override of F&O contract-resolution settings
 * (expiry/strike/option-type selection, lots, an optional explicit lot
 * size) plus the automatic profit-target ({@code targetPoints}). When no
 * row exists, TradingEngineService falls back to the server-config defaults
 * (trading.option.*, trading.quantity.lots, trading.exit.target.default-points)
 * - this table only ever stores an override, never a hardcoded lot-size
 * mapping; {@code lotSize} here is just "the value the user picked", still
 * validated against Groww's real instrument master on every use. Unique on
 * {@code (user_id, underlying)}.
 */
@Entity
@Table(name = "fno_trade_config",
        uniqueConstraints = @UniqueConstraint(name = "uq_fno_trade_config_user_underlying", columnNames = {"user_id", "underlying"}))
@Getter
@Setter
@NoArgsConstructor
public class FnoTradeConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "underlying", nullable = false, length = 20)
    private String underlying;

    @Column(name = "expiry_selection", nullable = false, length = 10)
    private String expirySelection;

    @Column(name = "strike_selection", nullable = false, length = 10)
    private String strikeSelection;

    @Column(name = "strike_offset", nullable = false)
    private int strikeOffset;

    @Column(name = "option_type", nullable = false, length = 10)
    private String optionType;

    @Column(name = "lots", nullable = false)
    private int lots;

    /** Null means "no explicit user override - use the resolved contract's own current lot size". */
    @Column(name = "lot_size")
    private Integer lotSize;

    /**
     * Automatic profit-target size in points. TARGET PRICE = actual filled
     * entry price + targetPoints. Null means "use
     * trading.exit.target.default-points". Never a target PRICE - the price
     * is always derived from the real fill.
     */
    @Column(name = "target_points", precision = 18, scale = 4)
    private BigDecimal targetPoints;

    /** Per-underlying toggle for the automatic target exit. */
    @Column(name = "target_enabled", nullable = false)
    private boolean targetEnabled = true;

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
