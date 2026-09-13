package com.example.trading.entity;

import com.example.trading.enums.SignalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The individual outcome of fanning one common TradingView signal out to
 * one connected Groww user (see {@code TradingEngineService#processSignal}
 * and {@code GrowwUserResolver}). {@code trading_signal} stays exactly one
 * row per webhook alert; this table holds one row per (signal, user) pair -
 * unique on that combination, so the same signal can never be recorded as
 * executed twice for the same user.
 */
@Entity
@Table(name = "signal_execution", uniqueConstraints = @UniqueConstraint(columnNames = {"signal_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SignalExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false, length = 100)
    private String signalId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    public static SignalExecutionEntity forSignalAndUser(String signalId, Long userId) {
        SignalExecutionEntity entity = new SignalExecutionEntity();
        entity.setSignalId(signalId);
        entity.setUserId(userId);
        return entity;
    }
}
