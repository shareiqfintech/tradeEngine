package com.example.trading.repository;

import com.example.trading.entity.FnoTradeConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FnoTradeConfigRepository extends JpaRepository<FnoTradeConfigEntity, Long> {

    /** Per-user, per-underlying override (unique on {@code (user_id, underlying)}). */
    Optional<FnoTradeConfigEntity> findByUserIdAndUnderlying(Long userId, String underlying);
}
