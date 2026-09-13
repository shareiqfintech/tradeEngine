package com.example.trading.scheduler;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.Position;
import com.example.trading.entity.OrderEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OrderStatus;
import com.example.trading.enums.TradingAction;
import com.example.trading.enums.TradingMode;
import com.example.trading.repository.OrderRepository;
import com.example.trading.service.AuditService;
import com.example.trading.service.GrowwOrderStatusService;
import com.example.trading.service.GrowwPositionService;
import com.example.trading.service.GrowwUserResolver;
import com.example.trading.service.MarketHoursService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * During market hours, periodically re-fetches order status for every
 * locally-open order and compares local COMPLETE-order-derived positions
 * against what Groww actually reports, raising a
 * {@code RECONCILIATION_MISMATCH} audit event whenever the two disagree.
 * Only meaningful in LIVE mode - PAPER orders never touch the broker, so
 * there is nothing to reconcile. Runs once per connected user, scoped to
 * that user's own orders/positions ({@code orders.user_id}) - one user's
 * failure never stops reconciliation for the others.
 */
@Slf4j
@Component
public class OrderReconciliationScheduler {

    private final OrderRepository orderRepository;
    private final GrowwOrderStatusService growwOrderStatusService;
    private final GrowwPositionService growwPositionService;
    private final MarketHoursService marketHoursService;
    private final AuditService auditService;
    private final TradingProperties properties;
    private final GrowwUserResolver growwUserResolver;

    public OrderReconciliationScheduler(OrderRepository orderRepository,
                                         GrowwOrderStatusService growwOrderStatusService,
                                         GrowwPositionService growwPositionService,
                                         MarketHoursService marketHoursService,
                                         AuditService auditService,
                                         TradingProperties properties,
                                         GrowwUserResolver growwUserResolver) {
        this.orderRepository = orderRepository;
        this.growwOrderStatusService = growwOrderStatusService;
        this.growwPositionService = growwPositionService;
        this.marketHoursService = marketHoursService;
        this.auditService = auditService;
        this.properties = properties;
        this.growwUserResolver = growwUserResolver;
    }

    @Scheduled(fixedRateString = "PT60S", initialDelayString = "PT30S")
    public void reconcile() {
        if (properties.getMode() != TradingMode.LIVE) {
            return;
        }
        if (!marketHoursService.isMarketOpenNow()) {
            return;
        }

        // Every connected user's own orders/positions are reconciled
        // independently against their own Groww account - one user's
        // failure (auth expired, broker error, ...) must not stop
        // reconciliation for the others.
        for (Long userId : growwUserResolver.findAllConnectedUserIds()) {
            try {
                refreshOpenOrders(userId);
                reconcilePositions(userId);
            } catch (Exception ex) {
                log.warn("RECONCILIATION_FAILED_FOR_USER userId={} reason={}", userId, ex.getMessage());
            }
        }
    }

    private void refreshOpenOrders(Long userId) {
        List<OrderEntity> openOrders = orderRepository.findByUserIdAndStatusIn(userId, List.of(OrderStatus.PENDING, OrderStatus.OPEN));
        for (OrderEntity order : openOrders) {
            try {
                growwOrderStatusService.refreshByGrowwOrderId(userId, order);
            } catch (Exception ex) {
                log.warn("RECONCILIATION_ORDER_REFRESH_FAILED orderRef={} reason={}", order.getOrderReferenceId(), ex.getMessage());
            }
        }
    }

    private void reconcilePositions(Long userId) {
        Map<String, Integer> localNetByTradingSymbol = computeLocalNetPositions(userId);
        List<Position> brokerPositions = growwPositionService.getPositions(userId);
        Map<String, Integer> brokerNetByTradingSymbol = new HashMap<>();
        for (Position position : brokerPositions) {
            brokerNetByTradingSymbol.put(position.getTradingSymbol(), position.getNetQuantity());
        }

        for (Map.Entry<String, Integer> entry : localNetByTradingSymbol.entrySet()) {
            String tradingSymbol = entry.getKey();
            int localNet = entry.getValue();
            int brokerNet = brokerNetByTradingSymbol.getOrDefault(tradingSymbol, 0);
            if (localNet != brokerNet) {
                String details = "userId=" + userId + " tradingSymbol=" + tradingSymbol + " localNet=" + localNet + " brokerNet=" + brokerNet;
                log.warn("RECONCILIATION_MISMATCH {}", details);
                auditService.record(AuditEventType.RECONCILIATION_MISMATCH, null, null, details);
            }
        }
    }

    private Map<String, Integer> computeLocalNetPositions(Long userId) {
        ZoneId zone = properties.getZoneId();
        Instant startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant endOfDay = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();

        Map<String, Integer> net = new HashMap<>();
        for (OrderEntity order : orderRepository.findByUserIdAndStatusAndCreatedAtBetween(userId, OrderStatus.COMPLETE, startOfDay, endOfDay)) {
            int signedQuantity = order.getAction() == TradingAction.BUY ? order.getFilledQuantity() : -order.getFilledQuantity();
            net.merge(order.getTradingSymbol(), signedQuantity, Integer::sum);
        }
        return net;
    }
}
