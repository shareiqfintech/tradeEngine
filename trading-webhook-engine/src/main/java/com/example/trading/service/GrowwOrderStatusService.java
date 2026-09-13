package com.example.trading.service;

import com.example.trading.entity.OrderEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OrderStatus;
import com.example.trading.exception.GrowwApiException;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.dto.GrowwOrderResponse;
import com.example.trading.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries Groww's order-status endpoints and reconciles the result into the
 * local {@code orders} row. Used both right after placing a live order and
 * by {@code OrderReconciliationScheduler} during market hours.
 */
@Slf4j
@Service
public class GrowwOrderStatusService {

    private static final String SEGMENT_FNO = "FNO";

    private final GrowwApiClient growwApiClient;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    public GrowwOrderStatusService(GrowwApiClient growwApiClient, OrderRepository orderRepository, AuditService auditService) {
        this.growwApiClient = growwApiClient;
        this.orderRepository = orderRepository;
        this.auditService = auditService;
    }

    /** Fetches the latest status by Groww order id (using {@code userId}'s own access token) and applies it to {@code order}. */
    @Transactional
    public OrderEntity refreshByGrowwOrderId(Long userId, OrderEntity order) {
        if (order.getGrowwOrderId() == null || order.getGrowwOrderId().isBlank()) {
            return order;
        }
        try {
            GrowwOrderResponse response = growwApiClient.getOrderStatus(userId, order.getGrowwOrderId(), SEGMENT_FNO);
            return applyStatus(order, response);
        } catch (GrowwApiException ex) {
            log.warn("ORDER_STATUS_CHECK_FAILED orderRef={} growwOrderId={} reason={}",
                    order.getOrderReferenceId(), order.getGrowwOrderId(), ex.getMessage());
            return order;
        }
    }

    /** Fallback lookup after a create-order call timed out on our side - see {@code TradingEngineService}. */
    public GrowwOrderResponse lookupByReference(Long userId, String orderReferenceId) {
        return growwApiClient.getOrderStatusByReference(userId, orderReferenceId, SEGMENT_FNO);
    }

    @Transactional
    public OrderEntity applyStatus(OrderEntity order, GrowwOrderResponse response) {
        if (response == null) {
            return order;
        }
        OrderStatus newStatus = mapStatus(response.getOrderStatus());
        boolean changed = order.getStatus() != newStatus;

        if (response.getGrowwOrderId() != null) {
            order.setGrowwOrderId(response.getGrowwOrderId());
        }
        order.setStatus(newStatus);
        if (response.getFilledQuantity() != null) {
            order.setFilledQuantity(response.getFilledQuantity());
        }
        if (response.getAverageFillPrice() != null) {
            order.setAverageFillPrice(response.getAverageFillPrice());
        }
        if (response.getRemark() != null) {
            order.setBrokerRemark(response.getRemark());
        }
        OrderEntity saved = orderRepository.save(order);

        if (changed) {
            AuditEventType eventType = newStatus == OrderStatus.COMPLETE ? AuditEventType.ORDER_COMPLETED
                    : newStatus == OrderStatus.REJECTED ? AuditEventType.ORDER_REJECTED
                    : AuditEventType.ORDER_STATUS_UPDATED;
            auditService.record(eventType, saved.getSignalId(), saved.getOrderReferenceId(),
                    "growwOrderId=" + saved.getGrowwOrderId() + " status=" + newStatus
                            + " filledQty=" + saved.getFilledQuantity());
        }
        return saved;
    }

    public OrderStatus mapStatus(String growwOrderStatus) {
        if (growwOrderStatus == null) {
            return OrderStatus.PENDING;
        }
        try {
            return OrderStatus.valueOf(growwOrderStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("UNKNOWN_GROWW_ORDER_STATUS value={}", growwOrderStatus);
            return OrderStatus.FAILED;
        }
    }
}
