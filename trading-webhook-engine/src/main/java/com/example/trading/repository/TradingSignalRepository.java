package com.example.trading.repository;

import com.example.trading.entity.TradingSignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;

public interface TradingSignalRepository extends JpaRepository<TradingSignalEntity, Long>,
        JpaSpecificationExecutor<TradingSignalEntity> {

    Optional<TradingSignalEntity> findBySignalId(String signalId);

    boolean existsBySignalId(String signalId);

    long countByUnderlyingAndCreatedAtBetween(String underlying, Instant from, Instant to);
}
