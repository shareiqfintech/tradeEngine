package com.example.trading.repository;

import com.example.trading.entity.OrderEntity;
import com.example.trading.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>,
        JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByOrderReferenceId(String orderReferenceId);

    Optional<OrderEntity> findByGrowwOrderId(String growwOrderId);

    Optional<OrderEntity> findFirstBySignalIdOrderByCreatedAtDesc(String signalId);

    List<OrderEntity> findByStatusIn(List<OrderStatus> statuses);

    List<OrderEntity> findByStatusAndCreatedAtBetween(OrderStatus status, Instant from, Instant to);

    /** Scoped to one user's own orders - used for per-user reconciliation now that a signal can execute for many users at once. */
    List<OrderEntity> findByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

    /** Scoped to one user's own orders - used for per-user reconciliation now that a signal can execute for many users at once. */
    List<OrderEntity> findByUserIdAndStatusAndCreatedAtBetween(Long userId, OrderStatus status, Instant from, Instant to);

    long countBySignalIdAndCreatedAtBetween(String signalId, Instant from, Instant to);

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByUnderlyingAndCreatedAtBetween(String underlying, Instant from, Instant to);
}
