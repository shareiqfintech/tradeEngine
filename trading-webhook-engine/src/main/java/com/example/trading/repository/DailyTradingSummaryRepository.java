package com.example.trading.repository;

import com.example.trading.entity.DailyTradingSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyTradingSummaryRepository extends JpaRepository<DailyTradingSummaryEntity, Long> {

    Optional<DailyTradingSummaryEntity> findByTradingDate(LocalDate tradingDate);
}
