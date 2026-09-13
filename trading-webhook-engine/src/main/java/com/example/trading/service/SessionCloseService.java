package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.Position;
import com.example.trading.entity.OrderEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OrderStatus;
import com.example.trading.enums.SessionState;
import com.example.trading.enums.TradingAction;
import com.example.trading.enums.TradingMode;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.redis.DistributedLockService;
import com.example.trading.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The 15:10 IST SAFETY EXIT: once the strategy trading cutoff passes, every
 * <b>system-managed</b> open position is automatically closed using the
 * <b>actual broker quantity</b> (never {@code configuredLots * lotSize}).
 *
 * <p><b>System-managed</b> = the broker position's trading symbol matches a
 * {@code COMPLETE} order this engine placed for that user <i>today</i>
 * (Asia/Kolkata). A position the user opened manually in the Groww app has
 * no such {@code orders} row and is never touched.
 *
 * <p>Runs under a per-day Redis lock ({@code trading:session-close:<date>})
 * so only one application instance performs the close. Close orders use a
 * deterministic reference ({@link OrderReferenceGenerator#sessionClose}) so
 * a scheduler double-fire, the 15:10-15:30 monitor loop, or an application
 * restart in the exit window reconcile an already-submitted order instead
 * of placing a second one.
 */
@Slf4j
@Service
public class SessionCloseService {

    private static final String LOCK_KEY_PREFIX = "trading:session-close:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final TradingProperties properties;
    private final TradingSessionService tradingSessionService;
    private final GrowwUserResolver growwUserResolver;
    private final GrowwAuthenticationService growwAuthenticationService;
    private final GrowwPositionService growwPositionService;
    private final GrowwOrderStatusService growwOrderStatusService;
    private final OrderExecutionSupport orderExecutionSupport;
    private final OrderReferenceGenerator orderReferenceGenerator;
    private final OrderRepository orderRepository;
    private final DistributedLockService distributedLockService;
    private final AuditService auditService;

    public SessionCloseService(TradingProperties properties,
                               TradingSessionService tradingSessionService,
                               GrowwUserResolver growwUserResolver,
                               GrowwAuthenticationService growwAuthenticationService,
                               GrowwPositionService growwPositionService,
                               GrowwOrderStatusService growwOrderStatusService,
                               OrderExecutionSupport orderExecutionSupport,
                               OrderReferenceGenerator orderReferenceGenerator,
                               OrderRepository orderRepository,
                               DistributedLockService distributedLockService,
                               AuditService auditService) {
        this.properties = properties;
        this.tradingSessionService = tradingSessionService;
        this.growwUserResolver = growwUserResolver;
        this.growwAuthenticationService = growwAuthenticationService;
        this.growwPositionService = growwPositionService;
        this.growwOrderStatusService = growwOrderStatusService;
        this.orderExecutionSupport = orderExecutionSupport;
        this.orderReferenceGenerator = orderReferenceGenerator;
        this.orderRepository = orderRepository;
        this.distributedLockService = distributedLockService;
        this.auditService = auditService;
    }

    /**
     * Entry point for the 15:10 scheduler and for restart recovery in the
     * 15:10-15:30 window. Idempotent: safe to call repeatedly.
     */
    public void startSafetyPositionClose() {
        if (!properties.getSession().isEnabled() || !properties.getSession().isAutoClosePositions()) {
            log.info("SESSION_AUTO_CLOSE_DISABLED enabled={} autoClose={}",
                    properties.getSession().isEnabled(), properties.getSession().isAutoClosePositions());
            tradingSessionService.beginSessionClosing();
            return;
        }

        LocalDate sessionDate = tradingSessionService.todayIst();
        String lockKey = LOCK_KEY_PREFIX + sessionDate;
        Optional<String> lockToken = distributedLockService.tryLock(lockKey, LOCK_TTL);
        if (lockToken.isEmpty()) {
            log.info("SESSION_CLOSE_SKIPPED reason=LOCK_HELD_BY_ANOTHER_INSTANCE key={}", lockKey);
            return;
        }

        try {
            tradingSessionService.beginSessionClosing();

            boolean allClear = true;
            for (Long userId : growwUserResolver.findAllConnectedUserIds()) {
                try {
                    allClear &= closeForUser(userId, sessionDate);
                } catch (Exception ex) {
                    allClear = false;
                    log.error("SESSION_CLOSE_FAILED_FOR_USER userId={} date={}", userId, sessionDate, ex);
                    auditService.record(AuditEventType.AUTO_CLOSE_ORDER_FAILED, null, null,
                            "userId=" + userId + " sessionDate=" + sessionDate + " reason=" + ex.getMessage());
                }
            }

            if (allClear) {
                tradingSessionService.markSafetyExitComplete();
            } else {
                log.warn("SESSION_CLOSE_INCOMPLETE date={} - staying SESSION_CLOSING, monitor will retry", sessionDate);
            }
        } finally {
            distributedLockService.unlock(lockKey, lockToken.get());
        }
    }

    /**
     * The 15:10-15:30 monitor (called every minute by the scheduler): while
     * the lifecycle is still {@code SESSION_CLOSING}, retry the close for
     * any system-managed position that is still open, and reconcile
     * in-flight close orders.
     */
    public void monitorSafetyExit() {
        if (tradingSessionService.getLifecycleState() != SessionState.SESSION_CLOSING) {
            return;
        }
        log.info("SESSION_CLOSE_MONITOR_TICK date={}", tradingSessionService.todayIst());
        startSafetyPositionClose();
    }

    // ------------------------------------------------------------------

    /** @return true when this user has no system-managed position left open. */
    private boolean closeForUser(Long userId, LocalDate sessionDate) {
        if (!growwAuthenticationService.isAuthenticated(userId) && !growwAuthenticationService.authenticate(userId)) {
            auditService.record(AuditEventType.AUTO_CLOSE_ORDER_FAILED, null, null,
                    "userId=" + userId + " sessionDate=" + sessionDate + " reason=GROWW_NOT_AUTHENTICATED");
            return false;
        }

        Set<String> managedSymbols = systemManagedSymbols(userId, sessionDate);
        if (managedSymbols.isEmpty()) {
            log.info("SESSION_CLOSE_NOTHING_TO_DO userId={} date={}", userId, sessionDate);
            return true;
        }

        List<Position> positions = growwPositionService.getPositions(userId);
        boolean clear = true;

        for (Position pos : positions) {
            String symbol = pos.getTradingSymbol();
            if (symbol == null || !managedSymbols.contains(symbol)) {
                continue; // manually opened / unrelated Groww position - MUST NOT be closed by this system
            }
            int net = pos.getNetQuantity() == null ? 0 : pos.getNetQuantity();
            if (net == 0) {
                continue; // already flat
            }
            if (net < 0 && !properties.isAllowShortSelling()) {
                // Short selling is disabled, so this engine never opened a short.
                log.warn("SESSION_CLOSE_SKIP_SHORT userId={} symbol={} netQty={} allowShortSelling=false", userId, symbol, net);
                continue;
            }

            TradingAction closeAction = net > 0 ? TradingAction.SELL : TradingAction.BUY;
            int qty = Math.abs(net); // ACTUAL broker quantity - never configuredLots * lotSize
            String ref = orderReferenceGenerator.sessionClose(sessionDate, userId, symbol);

            Optional<OrderEntity> existing = orderRepository.findByOrderReferenceId(ref);
            if (existing.isPresent()) {
                // Deterministic ref already used -> a close was already submitted.
                // Reconcile it, never blindly resubmit.
                OrderEntity prior = existing.get();
                if (properties.getMode() != TradingMode.PAPER) {
                    prior = growwOrderStatusService.refreshByGrowwOrderId(userId, prior);
                }
                boolean done = prior.getStatus() == OrderStatus.COMPLETE;
                if (!done) {
                    clear = false;
                }
                log.info("SESSION_CLOSE_ALREADY_SUBMITTED userId={} symbol={} ref={} status={}", userId, symbol, ref, prior.getStatus());
                continue;
            }

            OrderEntity closeOrder = orderRepository.save(newCloseOrder(userId, sessionDate, ref, pos, closeAction, qty));
            auditService.record(AuditEventType.AUTO_CLOSE_POSITION_STARTED, closeOrder.getSignalId(), ref,
                    "userId=" + userId + " symbol=" + symbol + " side=" + (net > 0 ? "LONG" : "SHORT")
                            + " brokerNetQty=" + net + " closeAction=" + closeAction + " qty=" + qty);

            try {
                if (properties.getMode() == TradingMode.PAPER) {
                    simulatePaperClose(closeOrder);
                } else {
                    auditService.record(AuditEventType.AUTO_CLOSE_ORDER_SUBMITTED, closeOrder.getSignalId(), ref,
                            "userId=" + userId + " symbol=" + symbol + " qty=" + qty);
                    closeOrder = orderExecutionSupport.submitAndReconcile(userId, closeOrder, pos.getExchange());
                }

                if (closeOrder.getStatus() == OrderStatus.COMPLETE) {
                    auditService.record(AuditEventType.AUTO_CLOSE_ORDER_FILLED, closeOrder.getSignalId(), ref,
                            "userId=" + userId + " symbol=" + symbol + " status=" + closeOrder.getStatus()
                                    + " filledQty=" + closeOrder.getFilledQuantity());
                } else {
                    clear = false;
                    auditService.record(AuditEventType.AUTO_CLOSE_ORDER_FAILED, closeOrder.getSignalId(), ref,
                            "userId=" + userId + " symbol=" + symbol + " status=" + closeOrder.getStatus() + " - not fully filled");
                }
            } catch (Exception ex) {
                clear = false;
                log.error("SESSION_CLOSE_ORDER_FAILED userId={} symbol={} ref={}", userId, symbol, ref, ex);
                auditService.record(AuditEventType.AUTO_CLOSE_ORDER_FAILED, closeOrder.getSignalId(), ref,
                        "userId=" + userId + " symbol=" + symbol + " reason=" + ex.getMessage());
            }
        }
        return clear;
    }

    private Set<String> systemManagedSymbols(Long userId, LocalDate sessionDate) {
        Instant from = sessionDate.atStartOfDay(properties.getZoneId()).toInstant();
        Instant to = sessionDate.plusDays(1).atStartOfDay(properties.getZoneId()).toInstant();
        Set<String> symbols = new LinkedHashSet<>();
        for (OrderEntity order : orderRepository.findByUserIdAndStatusAndCreatedAtBetween(userId, OrderStatus.COMPLETE, from, to)) {
            if (order.getTradingSymbol() != null) {
                symbols.add(order.getTradingSymbol());
            }
        }
        return symbols;
    }

    private OrderEntity newCloseOrder(Long userId, LocalDate sessionDate, String ref, Position pos,
                                      TradingAction action, int quantity) {
        OrderEntity order = new OrderEntity();
        order.setSignalId("SESSION_CLOSE:" + sessionDate);
        order.setUserId(userId);
        order.setOrderReferenceId(ref);
        order.setUnderlying(deriveUnderlying(pos.getTradingSymbol()));
        order.setTradingSymbol(pos.getTradingSymbol());
        order.setAction(action);
        order.setQuantity(quantity);
        order.setPrice(null);
        order.setOrderType(properties.getOrder().getOrderType());
        order.setProduct(pos.getProduct() != null ? pos.getProduct() : properties.getOrder().getProduct());
        order.setSegment("FNO");
        order.setStatus(OrderStatus.PENDING);
        order.setFilledQuantity(0);
        return order;
    }

    private void simulatePaperClose(OrderEntity order) {
        order.setGrowwOrderId("PAPER-" + order.getOrderReferenceId());
        order.setStatus(OrderStatus.COMPLETE);
        order.setFilledQuantity(order.getQuantity());
        order.setBrokerRemark("Simulated safety-exit close (PAPER mode) - no order was sent to Groww");
        orderRepository.save(order);
    }

    private String deriveUnderlying(String tradingSymbol) {
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            return "UNKNOWN";
        }
        String leadingLetters = tradingSymbol.replaceAll("^([A-Za-z]+).*", "$1");
        String underlying = leadingLetters.isBlank() ? tradingSymbol : leadingLetters;
        return underlying.length() > 20 ? underlying.substring(0, 20) : underlying;
    }
}
