package com.example.trading.repository;

import com.example.trading.entity.SignalExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignalExecutionRepository extends JpaRepository<SignalExecutionEntity, Long> {

    Optional<SignalExecutionEntity> findBySignalIdAndUserId(String signalId, Long userId);

    /** Every per-user outcome recorded so far for one signal - used to compute the aggregate signal.status after a fan-out. */
    List<SignalExecutionEntity> findBySignalId(String signalId);
}
