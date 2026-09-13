package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.OptionContract;
import com.example.trading.dto.Position;
import com.example.trading.entity.OrderEntity;
import com.example.trading.entity.PositionTargetEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OrderStatus;
import com.example.trading.enums.PositionSide;
import com.example.trading.enums.TargetStatus;
import com.example.trading.enums.TradingAction;
import com.example.trading.enums.TradingMode;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.redis.DistributedLockService;
import com.example.trading.repository.OrderRepository;
import com.example.trading.repository.PositionTargetRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Automatic profit-target exit (TARGET-ONLY, no stop-loss).
 *
 * <p>Lifecycle of one {@link PositionTargetEntity}:
 * <ol>
 *   <li>{@link #onEntryFilled} - called by {@code TradingEngineService} right after a BUY entry
 *       order is dispatched. If Groww confirmed a real fill price the row goes straight to
 *       {@code MONITORING} with {@code targetPrice = fill + targetPoints} (snapshotted); otherwise
 *       {@code PENDING_ENTRY} (section 18 - never computed from the signal price).</li>
 *   <li>{@link #resolvePendingEntries} - promotes {@code PENDING_ENTRY} rows once a real fill price
 *       is available.</li>
 *   <li>{@link #runMonitorTick} - the single poller: one batched LTP call per (user, exchange,
 *       segment), then {@link #checkAndExit} per row. On {@code LTP >= targetPrice} it submits
 *       <b>exactly one</b> SELL (distributed lock + {@code @Version} + deterministic order ref).</li>
 *   <li>{@link #recoverOnStartup} - reconciles in-flight exits after a restart; monitoring itself
 *       resumes automatically because all state is in the DB row.</li>
 * </ol>
 */
@Slf4j
@Service
public class PositionTargetService {

    private static final Duration EXIT_LOCK_TTL = Duration.ofSeconds(120);
    private static final List<TargetStatus> ACTIVE_MONITOR_STATES =
            List.of(TargetStatus.MONITORING, TargetStatus.TARGET_HIT, TargetStatus.EXIT_FAILED);

    private final PositionTargetRepository repository;
    private final TargetCalculator targetCalculator;
    private final TradingProperties properties;
    private final GrowwApiClient growwApiClient;
    private final GrowwAuthenticationService growwAuthenticationService;
    private final GrowwPositionService growwPositionService;
    private final GrowwOrderStatusService growwOrderStatusService;
    private final OrderExecutionSupport orderExecutionSupport;
    private final OrderRepository orderRepository;
    private final OrderReferenceGenerator orderReferenceGenerator;
    private final DistributedLockService distributedLockService;
    private final AuditService auditService;

    public PositionTargetService(PositionTargetRepository repository,
                                 TargetCalculator targetCalculator,
                                 TradingProperties properties,
                                 GrowwApiClient growwApiClient,
                                 GrowwAuthenticationService growwAuthenticationService,
                                 GrowwPositionService growwPositionService,
                                 GrowwOrderStatusService growwOrderStatusService,
                                 OrderExecutionSupport orderExecutionSupport,
                                 OrderRepository orderRepository,
                                 OrderReferenceGenerator orderReferenceGenerator,
                                 DistributedLockService distributedLockService,
                                 AuditService auditService) {
        this.repository = repository;
        this.targetCalculator = targetCalculator;
        this.properties = properties;
        this.growwApiClient = growwApiClient;
        this.growwAuthenticationService = growwAuthenticationService;
        this.growwPositionService = growwPositionService;
        this.growwOrderStatusService = growwOrderStatusService;
        this.orderExecutionSupport = orderExecutionSupport;
        this.orderRepository = orderRepository;
        this.orderReferenceGenerator = orderReferenceGenerator;
        this.distributedLockService = distributedLockService;
        this.auditService = auditService;
    }

    private TradingProperties.Exit.Target cfg() {
        return properties.getExit().getTarget();
    }

    // ------------------------------------------------------------------
    // 1. Entry -> create the target record
    // ------------------------------------------------------------------

    /**
     * @param entryOrder    the persisted BUY entry order, already through dispatch (PAPER simulate /
     *                      LIVE submit+reconcile) - {@code averageFillPrice} is the ACTUAL Groww fill
     * @param contract      the resolved contract that was bought (for exchange/segment/underlying)
     * @param targetPoints  the EFFECTIVE Target Points for this user/underlying (already resolved by
     *                      {@code FnoTradeConfigService})
     * @param targetEnabled the per-underlying toggle
     */
    public void onEntryFilled(OrderEntity entryOrder, OptionContract contract, BigDecimal targetPoints, boolean targetEnabled) {
        if (!cfg().isEnabled() || !targetEnabled) {
            log.info("TARGET_SKIPPED_DISABLED entryOrderRef={} featureEnabled={} underlyingEnabled={}",
                    entryOrder.getOrderReferenceId(), cfg().isEnabled(), targetEnabled);
            return;
        }
        if (repository.findByEntryOrderReferenceId(entryOrder.getOrderReferenceId()).isPresent()) {
            return; // idempotent
        }

        BigDecimal points = (targetPoints != null && targetPoints.signum() > 0) ? targetPoints : cfg().getDefaultPoints();

        PositionTargetEntity pt = new PositionTargetEntity();
        pt.setUserId(entryOrder.getUserId());
        pt.setSignalId(entryOrder.getSignalId());
        pt.setEntryOrderReferenceId(entryOrder.getOrderReferenceId());
        pt.setGrowwEntryOrderId(entryOrder.getGrowwOrderId());
        pt.setTradingSymbol(entryOrder.getTradingSymbol());
        pt.setExchange(contract != null && contract.getExchange() != null ? contract.getExchange() : "NSE");
        pt.setSegment(contract != null && contract.getSegment() != null ? contract.getSegment() : "FNO");
        pt.setUnderlying(contract != null && contract.getUnderlying() != null ? contract.getUnderlying() : entryOrder.getUnderlying());
        pt.setSide(PositionSide.LONG);
        pt.setQuantity(entryOrder.getFilledQuantity() != null && entryOrder.getFilledQuantity() > 0
                ? entryOrder.getFilledQuantity() : entryOrder.getQuantity());
        pt.setTargetPoints(points);
        pt.setTargetEnabled(true);

        BigDecimal fill = entryOrder.getAverageFillPrice();
        if (fill != null && fill.signum() > 0) {
            startMonitoring(pt, fill, points);
        } else {
            pt.setTargetStatus(TargetStatus.PENDING_ENTRY);
            repository.save(pt);
            auditService.record(AuditEventType.TARGET_MONITOR_DEFERRED, pt.getSignalId(), pt.getEntryOrderReferenceId(),
                    "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol()
                            + " targetPoints=" + points + " - awaiting a valid actual fill price before monitoring");
        }
    }

    private void startMonitoring(PositionTargetEntity pt, BigDecimal fillPrice, BigDecimal points) {
        BigDecimal targetPrice = targetCalculator.targetPrice(fillPrice, points, cfg().getTickSize());
        pt.setEntryPrice(fillPrice);
        pt.setTargetPrice(targetPrice);
        pt.setTargetStatus(TargetStatus.MONITORING);
        repository.save(pt);
        auditService.record(AuditEventType.TARGET_MONITOR_STARTED, pt.getSignalId(), pt.getEntryOrderReferenceId(),
                "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol()
                        + " entryPrice=" + fillPrice + " targetPoints=" + points + " targetPrice=" + targetPrice
                        + " quantity=" + pt.getQuantity());
        log.info("TARGET_MONITOR_STARTED userId={} contract={} entryPrice={} targetPoints={} targetPrice={}",
                pt.getUserId(), pt.getTradingSymbol(), fillPrice, points, targetPrice);
    }

    // ------------------------------------------------------------------
    // 2. Promote PENDING_ENTRY rows once a real fill price is known
    // ------------------------------------------------------------------

    public void resolvePendingEntries() {
        for (PositionTargetEntity pt : repository.findByTargetStatusIn(List.of(TargetStatus.PENDING_ENTRY))) {
            try {
                Optional<OrderEntity> maybeOrder = orderRepository.findByOrderReferenceId(pt.getEntryOrderReferenceId());
                if (maybeOrder.isEmpty()) {
                    continue;
                }
                OrderEntity order = maybeOrder.get();
                BigDecimal fill = order.getAverageFillPrice();
                if ((fill == null || fill.signum() <= 0)
                        && properties.getMode() == TradingMode.LIVE
                        && order.getGrowwOrderId() != null) {
                    order = growwOrderStatusService.refreshByGrowwOrderId(pt.getUserId(), order);
                    fill = order.getAverageFillPrice();
                }
                if (fill != null && fill.signum() > 0) {
                    pt.setGrowwEntryOrderId(order.getGrowwOrderId());
                    if (order.getFilledQuantity() != null && order.getFilledQuantity() > 0) {
                        pt.setQuantity(order.getFilledQuantity());
                    }
                    startMonitoring(pt, fill, pt.getTargetPoints());
                }
            } catch (Exception ex) {
                log.warn("TARGET_PENDING_ENTRY_RESOLVE_FAILED id={} reason={}", pt.getId(), ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // 3. The single poll tick
    // ------------------------------------------------------------------

    public void runMonitorTick() {
        List<PositionTargetEntity> rows = repository.findByTargetStatusIn(ACTIVE_MONITOR_STATES);
        if (rows.isEmpty()) {
            return;
        }

        // Batch + de-duplicate LTP requests: one call per (user, exchange, segment) covering every
        // distinct contract that user is monitoring - N positions on the same contract cost 1 fetch.
        Map<String, BigDecimal> ltpByKey = fetchLtps(rows);

        for (PositionTargetEntity row : rows) {
            try {
                BigDecimal ltp = ltpByKey.get(ltpKey(row.getExchange(), row.getTradingSymbol()));
                checkAndExit(row, ltp);
            } catch (Exception ex) {
                log.error("TARGET_MONITOR_ROW_FAILED id={} contract={}", row.getId(), row.getTradingSymbol(), ex);
            }
        }
    }

    private Map<String, BigDecimal> fetchLtps(List<PositionTargetEntity> rows) {
        // Group the distinct symbols a user needs, per (exchange, segment).
        Map<String, Set<String>> symbolsByUserExchangeSegment = new LinkedHashMap<>();
        for (PositionTargetEntity row : rows) {
            if (row.getTargetStatus() != TargetStatus.MONITORING) {
                continue; // TARGET_HIT / EXIT_FAILED don't need an LTP
            }
            String group = row.getUserId() + "|" + row.getExchange() + "|" + row.getSegment();
            symbolsByUserExchangeSegment.computeIfAbsent(group, g -> new LinkedHashSet<>()).add(row.getTradingSymbol());
        }

        Map<String, BigDecimal> ltpByKey = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : symbolsByUserExchangeSegment.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 3);
            Long userId = Long.valueOf(parts[0]);
            String exchange = parts[1];
            String segment = parts[2];
            if (!growwAuthenticationService.isAuthenticated(userId) && !growwAuthenticationService.authenticate(userId)) {
                log.warn("TARGET_MONITOR_LTP_SKIPPED userId={} reason=GROWW_NOT_AUTHENTICATED", userId);
                continue;
            }
            try {
                Map<String, BigDecimal> ltps = growwApiClient.getLtps(userId, exchange, segment, new ArrayList<>(entry.getValue()));
                ltpByKey.putAll(ltps);
            } catch (Exception ex) {
                log.warn("TARGET_MONITOR_LTP_FETCH_FAILED userId={} symbols={} reason={}", userId, entry.getValue(), ex.getMessage());
            }
        }
        return ltpByKey;
    }

    private static String ltpKey(String exchange, String tradingSymbol) {
        return (exchange + "_" + tradingSymbol).toUpperCase(Locale.ROOT);
    }

    /**
     * For a MONITORING row: if {@code ltp} is null we never assume the target was reached
     * (section 18); if {@code ltp < targetPrice} nothing happens; otherwise the exit is driven.
     * For a TARGET_HIT / EXIT_FAILED row the exit is (re-)driven regardless of {@code ltp}.
     */
    void checkAndExit(PositionTargetEntity row, BigDecimal ltp) {
        if (row.getTargetStatus() == TargetStatus.MONITORING) {
            if (ltp == null) {
                auditService.record(AuditEventType.TARGET_LTP_UNAVAILABLE, row.getSignalId(), row.getEntryOrderReferenceId(),
                        "userId=" + row.getUserId() + " contract=" + row.getTradingSymbol()
                                + " - no live LTP this tick; NOT assuming target reached");
                return;
            }
            repository.updateLastLtp(row.getId(), ltp, Instant.now());
            if (row.getTargetPrice() == null || ltp.compareTo(row.getTargetPrice()) < 0) {
                return; // target not reached
            }
        }

        Optional<String> lockToken = distributedLockService.tryLock("trading:target-exit:" + row.getId(), EXIT_LOCK_TTL);
        if (lockToken.isEmpty()) {
            return; // another thread / instance is handling this exit
        }
        try {
            PositionTargetEntity pt = repository.findById(row.getId()).orElse(null);
            if (pt == null || pt.getTargetStatus() == TargetStatus.CLOSED || pt.getTargetStatus() == TargetStatus.CLOSING
                    || pt.getTargetStatus() == TargetStatus.PENDING_ENTRY) {
                return; // already handled / not exitable
            }

            if (pt.getTargetStatus() == TargetStatus.MONITORING) {
                pt.setTargetStatus(TargetStatus.TARGET_HIT);
                pt.setLtpAtTrigger(ltp);
                pt = repository.save(pt); // @Version guards a lost race -> exception -> caught below
                auditService.record(AuditEventType.TARGET_HIT, pt.getSignalId(), pt.getEntryOrderReferenceId(),
                        "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol()
                                + " targetPrice=" + pt.getTargetPrice() + " ltp=" + ltp);
                log.info("TARGET_HIT userId={} contract={} targetPrice={} ltp={}",
                        pt.getUserId(), pt.getTradingSymbol(), pt.getTargetPrice(), ltp);
            }

            submitExit(pt);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException ex) {
            log.info("TARGET_EXIT_RACE_LOST id={} - another worker owns this exit", row.getId());
        } finally {
            distributedLockService.unlock("trading:target-exit:" + row.getId(), lockToken.get());
        }
    }

    // ------------------------------------------------------------------
    // 4. Submit exactly one exit order and finalize
    // ------------------------------------------------------------------

    private void submitExit(PositionTargetEntity pt) {
        String ref = orderReferenceGenerator.targetExit(pt.getId());

        Optional<OrderEntity> existing = orderRepository.findByOrderReferenceId(ref);
        if (existing.isPresent()) {
            reconcileExit(pt, existing.get()); // restart mid-exit / retry -> never a second SELL
            return;
        }

        int exitQty = resolveExitQuantity(pt);
        if (exitQty <= 0) {
            pt.setTargetStatus(TargetStatus.CLOSED);
            pt.setExitedQuantity(0);
            pt.setClosedAt(Instant.now());
            repository.save(pt);
            auditService.record(AuditEventType.TARGET_POSITION_CLOSED, pt.getSignalId(), pt.getEntryOrderReferenceId(),
                    "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol()
                            + " - position already flat at the broker, nothing to sell");
            return;
        }

        // Non-transactional service: each repository.save() commits in its own tx, so the
        // in-memory @Version must be kept in sync by re-assigning from the return value -
        // otherwise the next save() fails an optimistic-lock check and the row would be
        // left stuck in CLOSING.

        OrderEntity exit = new OrderEntity();
        exit.setSignalId(pt.getSignalId());
        exit.setUserId(pt.getUserId());
        exit.setOrderReferenceId(ref);
        exit.setUnderlying(pt.getUnderlying());
        exit.setTradingSymbol(pt.getTradingSymbol());
        exit.setAction(TradingAction.SELL);
        exit.setQuantity(exitQty);
        exit.setOrderType(properties.getOrder().getOrderType());
        exit.setProduct(properties.getOrder().getProduct());
        exit.setSegment(pt.getSegment());
        exit.setStatus(OrderStatus.PENDING);
        exit.setFilledQuantity(0);
        exit = orderRepository.save(exit);

        pt.setExitOrderReferenceId(ref);
        pt.setTargetStatus(TargetStatus.CLOSING);
        pt = repository.save(pt);
        auditService.record(AuditEventType.TARGET_EXIT_ORDER_SUBMITTED, pt.getSignalId(), ref,
                "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol() + " action=SELL quantity=" + exitQty
                        + " targetPrice=" + pt.getTargetPrice());

        try {
            if (properties.getMode() == TradingMode.PAPER) {
                simulatePaperExit(exit);
            } else {
                exit = orderExecutionSupport.submitAndReconcile(pt.getUserId(), exit, pt.getExchange());
            }
        } catch (Exception ex) {
            pt.setTargetStatus(TargetStatus.EXIT_FAILED);
            pt.setGrowwExitOrderId(exit.getGrowwOrderId());
            repository.save(pt);
            auditService.record(AuditEventType.TARGET_EXIT_ORDER_FAILED, pt.getSignalId(), ref,
                    "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol() + " reason=" + ex.getMessage());
            log.error("TARGET_EXIT_ORDER_FAILED id={} contract={}", pt.getId(), pt.getTradingSymbol(), ex);
            return;
        }

        finalizeExit(pt, exit);
    }

    private void reconcileExit(PositionTargetEntity pt, OrderEntity exit) {
        if (properties.getMode() == TradingMode.LIVE && exit.getGrowwOrderId() != null) {
            exit = growwOrderStatusService.refreshByGrowwOrderId(pt.getUserId(), exit);
        }
        finalizeExit(pt, exit);
    }

    private void finalizeExit(PositionTargetEntity pt, OrderEntity exit) {
        pt.setGrowwExitOrderId(exit.getGrowwOrderId());
        if (exit.getStatus() == OrderStatus.COMPLETE) {
            pt.setTargetStatus(TargetStatus.CLOSED);
            pt.setExitedQuantity(exit.getFilledQuantity() != null ? exit.getFilledQuantity() : exit.getQuantity());
            pt.setClosedAt(Instant.now());
            pt = repository.save(pt);
            auditService.record(AuditEventType.TARGET_EXIT_ORDER_FILLED, pt.getSignalId(), pt.getExitOrderReferenceId(),
                    "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol()
                            + " filledQty=" + pt.getExitedQuantity() + " status=" + exit.getStatus());
            auditService.record(AuditEventType.TARGET_POSITION_CLOSED, pt.getSignalId(), pt.getExitOrderReferenceId(),
                    "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol()
                            + " entryPrice=" + pt.getEntryPrice() + " targetPoints=" + pt.getTargetPoints()
                            + " targetPrice=" + pt.getTargetPrice() + " ltpAtTrigger=" + pt.getLtpAtTrigger()
                            + " exitedQty=" + pt.getExitedQuantity());
            log.info("TARGET_POSITION_CLOSED userId={} contract={} exitedQty={}",
                    pt.getUserId(), pt.getTradingSymbol(), pt.getExitedQuantity());
        } else {
            pt.setTargetStatus(TargetStatus.EXIT_FAILED);
            repository.save(pt);
            auditService.record(AuditEventType.TARGET_EXIT_ORDER_FAILED, pt.getSignalId(), pt.getExitOrderReferenceId(),
                    "userId=" + pt.getUserId() + " contract=" + pt.getTradingSymbol()
                            + " status=" + exit.getStatus() + " - exit not fully filled, will retry");
        }
    }

    /** The actual quantity to sell now - never recalculated from the frontend / configured lots (section 13). */
    private int resolveExitQuantity(PositionTargetEntity pt) {
        int alreadyExited = pt.getExitedQuantity() != null ? pt.getExitedQuantity() : 0;
        if (properties.getMode() == TradingMode.PAPER) {
            return Math.max(0, pt.getQuantity() - alreadyExited);
        }
        // LIVE: the broker's own remaining net quantity for this EXACT contract is the source of truth.
        List<Position> positions = growwPositionService.getPositions(pt.getUserId());
        for (Position p : positions) {
            if (p.getTradingSymbol() != null && p.getTradingSymbol().equalsIgnoreCase(pt.getTradingSymbol())) {
                int net = p.getNetQuantity() == null ? 0 : Math.abs(p.getNetQuantity());
                return net;
            }
        }
        return 0; // broker has no such position -> already flat
    }

    private void simulatePaperExit(OrderEntity order) {
        order.setGrowwOrderId("PAPER-" + order.getOrderReferenceId());
        order.setStatus(OrderStatus.COMPLETE);
        order.setFilledQuantity(order.getQuantity());
        order.setBrokerRemark("Simulated target exit (PAPER mode) - no order was sent to Groww");
        orderRepository.save(order);
    }

    // ------------------------------------------------------------------
    // 5. Restart recovery
    // ------------------------------------------------------------------

    public void recoverOnStartup() {
        List<PositionTargetEntity> active = repository.findByTargetStatusIn(List.of(
                TargetStatus.PENDING_ENTRY, TargetStatus.MONITORING, TargetStatus.TARGET_HIT,
                TargetStatus.CLOSING, TargetStatus.EXIT_FAILED));
        if (active.isEmpty()) {
            return;
        }

        Map<TargetStatus, Long> counts = new LinkedHashMap<>();
        for (PositionTargetEntity pt : active) {
            counts.merge(pt.getTargetStatus(), 1L, Long::sum);
        }
        log.info("TARGET_MONITOR_RESUMED counts={}", counts);
        auditService.record(AuditEventType.TARGET_MONITOR_RESUMED, null, null,
                "resuming target monitoring after startup: " + counts);

        for (PositionTargetEntity pt : active) {
            if (pt.getTargetStatus() == TargetStatus.CLOSING && pt.getExitOrderReferenceId() != null) {
                try {
                    orderRepository.findByOrderReferenceId(pt.getExitOrderReferenceId())
                            .ifPresent(order -> reconcileExit(pt, order));
                } catch (Exception ex) {
                    log.warn("TARGET_STARTUP_RECONCILE_FAILED id={} reason={}", pt.getId(), ex.getMessage());
                }
            }
        }
        // MONITORING / PENDING_ENTRY / TARGET_HIT / EXIT_FAILED are picked up automatically by the scheduler.
    }
}
