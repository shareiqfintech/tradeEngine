package com.example.trading.repository;

import com.example.trading.entity.PositionTargetEntity;
import com.example.trading.enums.TargetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PositionTargetRepository extends JpaRepository<PositionTargetEntity, Long> {

    /** One target row per entry order - the idempotency key for creation. */
    Optional<PositionTargetEntity> findByEntryOrderReferenceId(String entryOrderReferenceId);

    List<PositionTargetEntity> findByTargetStatusIn(List<TargetStatus> statuses);

    List<PositionTargetEntity> findByUserIdAndTargetStatusIn(Long userId, List<TargetStatus> statuses);

    List<PositionTargetEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * Best-effort "last seen LTP" update for the dashboard - deliberately
     * NOT bumping {@code version}, and scoped to MONITORING so it can never
     * race the exit-state transition.
     */
    @Modifying
    @Transactional
    @Query("update PositionTargetEntity p set p.lastLtp = :ltp, p.lastLtpAt = :at "
            + "where p.id = :id and p.targetStatus = com.example.trading.enums.TargetStatus.MONITORING")
    void updateLastLtp(@Param("id") Long id, @Param("ltp") BigDecimal ltp, @Param("at") Instant at);
}
