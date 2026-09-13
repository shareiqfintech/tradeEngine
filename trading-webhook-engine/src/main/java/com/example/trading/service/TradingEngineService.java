package com.example.trading.service;

import com.example.trading.config.AppConfig;
import com.example.trading.config.TradingProperties;
import com.example.trading.dto.FnoResolutionParams;
import com.example.trading.dto.OptionContract;
import com.example.trading.dto.Position;
import com.example.trading.dto.RiskDecision;
import com.example.trading.entity.DailyTradingSummaryEntity;
import com.example.trading.entity.OrderEntity;
import com.example.trading.entity.SignalExecutionEntity;
import com.example.trading.entity.TradingSignalEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OptionType;
import com.example.trading.enums.OrderStatus;
import com.example.trading.enums.SignalStatus;
import com.example.trading.enums.TradingAction;
import com.example.trading.enums.TradingMode;
import com.example.trading.exception.AuthenticationRequiredException;
import com.example.trading.exception.ContractNotFoundException;
import com.example.trading.exception.OrderRejectedException;
import com.example.trading.exception.PositionCloseIncompleteException;
import com.example.trading.exception.PositionNotFoundException;
import com.example.trading.exception.RiskRejectedException;
import com.example.trading.exception.TradingSessionClosedException;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.redis.DistributedLockService;
import com.example.trading.repository.DailyTradingSummaryRepository;
import com.example.trading.repository.OrderRepository;
import com.example.trading.repository.SignalExecutionRepository;
import com.example.trading.repository.TradingSignalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the full life cycle of one signal, from "just persisted by
 * the webhook controller" to "order placed (or cleanly rejected) and
 * audited, for every connected user". Runs off the webhook request thread
 * (see {@link #processSignalAsync(String)}) so TradingView always gets its
 * 202 immediately.
 *
 * <p>A TradingView alert is one common signal, not a signal belonging to a
 * specific application user - it never selects a single "the" trading user.
 * Once the global gates pass (trading session window, kill switch / pause,
 * distributed lock), it is executed independently for every user who
 * currently has a connected Groww account (see {@link GrowwUserResolver}):
 * one user's failure never stops the others - see {@link #executeForUser}.
 *
 * <p><b>Session timing:</b> a NEW entry is only allowed 09:25-15:10 IST. The
 * check runs twice - once up front for the whole signal, and again as the
 * mandatory final guard immediately before each new-entry order is
 * dispatched ({@link #dispatchOrder}), so a signal delayed in the async
 * queue past 15:10 still never opens a position. Position-close / exit legs
 * are never gated by it. See {@link TradingSessionService}.
 */
@Slf4j
@Service
public class TradingEngineService {

    private static final String SEGMENT_FNO = "FNO";

    private final TradingSignalRepository signalRepository;
    private final SignalExecutionRepository signalExecutionRepository;
    private final OrderRepository orderRepository;
    private final DailyTradingSummaryRepository dailySummaryRepository;
    private final TradingStateService tradingStateService;
    private final TradingSessionService tradingSessionService;
    private final GrowwAuthenticationService growwAuthenticationService;
    private final PositionService positionService;
    private final OptionContractResolver optionContractResolver;
    private final FnoTradeConfigService fnoTradeConfigService;
    private final RiskManagementService riskManagementService;
    private final OrderReferenceGenerator orderReferenceGenerator;
    private final OrderExecutionSupport orderExecutionSupport;
    private final PositionTargetService positionTargetService;
    private final AuditService auditService;
    private final DistributedLockService distributedLockService;
    private final TradingProperties properties;
    private final GrowwUserResolver growwUserResolver;

    public TradingEngineService(TradingSignalRepository signalRepository,
                                 SignalExecutionRepository signalExecutionRepository,
                                 OrderRepository orderRepository,
                                 DailyTradingSummaryRepository dailySummaryRepository,
                                 TradingStateService tradingStateService,
                                 TradingSessionService tradingSessionService,
                                 GrowwAuthenticationService growwAuthenticationService,
                                 PositionService positionService,
                                 OptionContractResolver optionContractResolver,
                                 FnoTradeConfigService fnoTradeConfigService,
                                 RiskManagementService riskManagementService,
                                 OrderReferenceGenerator orderReferenceGenerator,
                                 OrderExecutionSupport orderExecutionSupport,
                                 PositionTargetService positionTargetService,
                                 AuditService auditService,
                                 DistributedLockService distributedLockService,
                                 TradingProperties properties,
                                 GrowwUserResolver growwUserResolver) {
        this.signalRepository = signalRepository;
        this.signalExecutionRepository = signalExecutionRepository;
        this.orderRepository = orderRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.tradingStateService = tradingStateService;
        this.tradingSessionService = tradingSessionService;
        this.growwAuthenticationService = growwAuthenticationService;
        this.positionService = positionService;
        this.optionContractResolver = optionContractResolver;
        this.fnoTradeConfigService = fnoTradeConfigService;
        this.riskManagementService = riskManagementService;
        this.orderReferenceGenerator = orderReferenceGenerator;
        this.orderExecutionSupport = orderExecutionSupport;
        this.positionTargetService = positionTargetService;
        this.auditService = auditService;
        this.distributedLockService = distributedLockService;
        this.properties = properties;
        this.growwUserResolver = growwUserResolver;
    }

    @Async(AppConfig.TRADING_EXECUTOR)
    public void processSignalAsync(String signalId) {
        processSignal(signalId);
    }

    /** Public (not just for @Async self-invocation reasons) so tests can call it directly and synchronously. */
    public void processSignal(String signalId) {
        Optional<TradingSignalEntity> maybeSignal = signalRepository.findBySignalId(signalId);
        if (maybeSignal.isEmpty()) {
            log.error("SIGNAL_NOT_FOUND signalId={}", signalId);
            return;
        }
        TradingSignalEntity signal = maybeSignal.get();
        if (signal.getStatus() == SignalStatus.DUPLICATE) {
            return; // already short-circuited at intake
        }

        try {
            // 1. Session window: a NEW entry is only allowed 09:25-15:10 IST.
            try {
                tradingSessionService.assertNewEntryAllowed();
            } catch (TradingSessionClosedException ex) {
                rejectSignal(signal, ex.getReasonCode(), ex.getMessage());
                auditService.record(sessionRejectAudit(ex.getReasonCode()), signal.getSignalId(), null, ex.getMessage());
                return;
            }

            // 2. Operator overrides.
            if (tradingStateService.isKillSwitchEnabled()) {
                rejectSignal(signal, "KILL_SWITCH_ENABLED", "Trading is not currently allowed");
                auditService.record(AuditEventType.TRADING_DISABLED, signal.getSignalId(), null, "KILL_SWITCH_ENABLED");
                return;
            }
            if (tradingStateService.isPaused()) {
                rejectSignal(signal, "TRADING_PAUSED", "Trading is not currently allowed");
                auditService.record(AuditEventType.TRADING_DISABLED, signal.getSignalId(), null, "TRADING_PAUSED");
                return;
            }

            // 3. A TradingView webhook does not belong to any one application
            // user: execute the common signal independently for EVERY user
            // who currently has a connected Groww account.
            List<Long> connectedUserIds = growwUserResolver.findAllConnectedUserIds();
            if (connectedUserIds.isEmpty()) {
                rejectSignal(signal, "GROWW_NOT_CONNECTED", "No application user currently has a connected Groww account to trade with");
                return;
            }

            Optional<String> lockToken = distributedLockService.tryLock(signal.getUnderlying(), Duration.ofSeconds(30));
            if (lockToken.isEmpty()) {
                rejectSignal(signal, "LOCK_BUSY", "Another signal for " + signal.getUnderlying() + " is already being processed");
                return;
            }
            try {
                markValidated(signal);
                for (Long userId : connectedUserIds) {
                    executeForUser(userId, signal);
                }
                finalizeSignal(signal);
            } finally {
                distributedLockService.unlock(signal.getUnderlying(), lockToken.get());
            }
        } catch (Exception ex) {
            log.error("SIGNAL_PROCESSING_FAILED signalId={}", signalId, ex);
            markFailed(signal, safeMessage(ex));
        }
    }

    private AuditEventType sessionRejectAudit(String reasonCode) {
        return TradingSessionClosedException.SESSION_NOT_STARTED.equals(reasonCode)
                ? AuditEventType.NEW_ENTRY_REJECTED_SESSION_NOT_STARTED
                : AuditEventType.NEW_ENTRY_REJECTED_TRADING_CUTOFF;
    }

    // ------------------------------------------------------------------
    // Per-user execution: everything that can go wrong for ONE connected
    // user (auth, risk, contract resolution, broker rejection, the 15:10
    // cutoff crossing mid-execution, ...) is caught here and recorded on
    // that user's own SignalExecutionEntity row - it never propagates out
    // and never stops the remaining users in the fan-out loop.
    // ------------------------------------------------------------------

    private void executeForUser(Long userId, TradingSignalEntity signal) {
        try {
            if (!growwAuthenticationService.isAuthenticated(userId) && !growwAuthenticationService.authenticate(userId)) {
                recordUserRejection(signal, userId, "AUTH_REQUIRED", "Groww authentication is not available");
                return;
            }

            OrderEntity finalOrder = signal.getAction() == TradingAction.BUY
                    ? handleBuy(userId, signal)
                    : handleSell(userId, signal);
            recordDispatchOutcome(signal, userId, finalOrder);
        } catch (TradingSessionClosedException ex) {
            // The 15:10 cutoff was crossed after this signal started processing
            // (async-queue / scheduler delay). No entry order was sent.
            recordUserRejection(signal, userId, ex.getReasonCode(), ex.getMessage());
            auditService.record(sessionRejectAudit(ex.getReasonCode()), signal.getSignalId(), null,
                    "userId=" + userId + " " + ex.getMessage());
        } catch (PositionNotFoundException ex) {
            recordUserRejection(signal, userId, "NO_LONG_POSITION", ex.getMessage());
        } catch (PositionCloseIncompleteException ex) {
            recordUserRejection(signal, userId, "POSITION_CLOSE_INCOMPLETE", ex.getMessage());
        } catch (ContractNotFoundException ex) {
            auditService.record(AuditEventType.CONTRACT_NOT_FOUND, signal.getSignalId(), null, ex.getMessage());
            recordUserRejection(signal, userId, "CONTRACT_NOT_FOUND", ex.getMessage());
        } catch (RiskRejectedException ex) {
            recordUserRejection(signal, userId, ex.getReasonCode(), ex.getMessage());
        } catch (AuthenticationRequiredException ex) {
            recordUserRejection(signal, userId, "AUTH_REQUIRED", ex.getMessage());
        } catch (OrderRejectedException ex) {
            recordUserRejection(signal, userId, "ORDER_REJECTED", ex.getMessage());
        } catch (Exception ex) {
            log.error("SIGNAL_EXECUTION_FAILED_FOR_USER signalId={} userId={}", signal.getSignalId(), userId, ex);
            recordUserFailure(signal, userId, safeMessage(ex));
        }
    }

    // ------------------------------------------------------------------
    // BULLISH (BUY signal): close any existing opposite-side (PE) position
    // first - using ITS OWN actual open quantity and exact contract - then
    // resolve and buy the nearest CE. If no PE is held, the close step is a
    // no-op and this behaves exactly as a plain BUY did before.
    // ------------------------------------------------------------------

    private OrderEntity handleBuy(Long userId, TradingSignalEntity signal) {
        closeOppositePositionIfHeld(userId, signal, OptionType.PE);
        return openNewOptionPosition(userId, signal, OptionType.CE);
    }

    // ------------------------------------------------------------------
    // BEARISH (SELL signal): mirror image of handleBuy - close any existing
    // CE position first, then buy the nearest PE. Still never opens a
    // short: the new PE is a fully paid long options purchase, not a short
    // sale (see TradingAction javadoc).
    // ------------------------------------------------------------------

    private OrderEntity handleSell(Long userId, TradingSignalEntity signal) {
        if (properties.isAllowShortSelling()) {
            // Explicitly out of scope for the initial implementation - see class javadoc / project README.
            log.warn("SHORT_SELLING_REQUESTED_BUT_NOT_IMPLEMENTED underlying={}", signal.getUnderlying());
        }

        closeOppositePositionIfHeld(userId, signal, OptionType.CE);
        return openNewOptionPosition(userId, signal, OptionType.PE);
    }

    // ------------------------------------------------------------------
    // Position-switching: close the opposite side, verify full closure
    // ------------------------------------------------------------------

    /**
     * If an active {@code oppositeType} position exists for this
     * underlying, closes it in full - using its own actual open quantity
     * (from the live Groww position, never assumed) and its own exact
     * trading symbol (never a freshly resolved contract) - and verifies the
     * close order actually filled that entire quantity. This is an EXIT leg
     * - never gated by the 15:10 new-entry cutoff.
     */
    private void closeOppositePositionIfHeld(Long userId, TradingSignalEntity signal, OptionType oppositeType) {
        Optional<Position> maybePosition = positionService.findActivePosition(userId, signal.getUnderlying(), oppositeType, signal.getSignalId());
        if (maybePosition.isEmpty()) {
            return;
        }
        Position existingPosition = maybePosition.get();
        int quantity = positionService.availableQuantityToSell(existingPosition);
        if (quantity <= 0) {
            return; // flat - nothing to close, per spec section 13
        }

        RiskDecision decision = riskManagementService.evaluate(userId, signal.getSignalId(), signal.getUnderlying(), quantity, null, false);
        if (!decision.isApproved()) {
            throw new RiskRejectedException(decision.getReasonCode(), decision.getReason());
        }

        String orderReferenceId = orderReferenceGenerator.generate(signal.getUnderlying());
        OrderEntity closeOrder = newOrderEntity(userId, signal, orderReferenceId, existingPosition.getTradingSymbol(), TradingAction.SELL, quantity);
        closeOrder.setProduct(existingPosition.getProduct() != null ? existingPosition.getProduct() : properties.getOrder().getProduct());
        closeOrder = orderRepository.save(closeOrder);

        dispatchOrder(userId, closeOrder, existingPosition.getExchange(), signal.getPrice(), false);

        verifyFullyClosed(signal, oppositeType, existingPosition, closeOrder, quantity);
    }

    /**
     * The mandatory check from spec section 2/6: placing a SELL does not
     * mean the position is closed. Only the broker's own filled quantity on
     * this exact close order decides that - never assumed.
     */
    private void verifyFullyClosed(TradingSignalEntity signal, OptionType oppositeType, Position existingPosition,
                                    OrderEntity closeOrder, int requestedQuantity) {
        Integer filled = closeOrder.getFilledQuantity();
        boolean fullyClosed = !isRejected(closeOrder) && filled != null && filled >= requestedQuantity;

        if (!fullyClosed) {
            int remaining = requestedQuantity - (filled == null ? 0 : filled);
            auditService.record(AuditEventType.POSITION_CLOSE_INCOMPLETE, signal.getSignalId(), closeOrder.getOrderReferenceId(),
                    "tradingSymbol=" + existingPosition.getTradingSymbol() + " requestedQuantity=" + requestedQuantity
                            + " filledQuantity=" + filled + " remaining=" + remaining + " orderStatus=" + closeOrder.getStatus());
            throw new PositionCloseIncompleteException("Existing " + oppositeType + " position "
                    + existingPosition.getTradingSymbol() + " was not fully closed (requested=" + requestedQuantity
                    + " filled=" + filled + " remaining=" + remaining + ")");
        }

        auditService.record(AuditEventType.POSITION_CLOSE_VERIFIED, signal.getSignalId(), closeOrder.getOrderReferenceId(),
                "tradingSymbol=" + existingPosition.getTradingSymbol() + " closedQuantity=" + requestedQuantity);
    }

    // ------------------------------------------------------------------
    // Opening the new (same-direction) contract - a NEW ENTRY
    // ------------------------------------------------------------------

    private OrderEntity openNewOptionPosition(Long userId, TradingSignalEntity signal, OptionType direction) {
        FnoResolutionParams configured = fnoTradeConfigService.getEffectiveParams(userId, signal.getUnderlying());
        FnoResolutionParams params = new FnoResolutionParams(direction.name(), configured.expirySelection(),
                configured.strikeSelection(), configured.strikeOffset(), configured.lots(), configured.lotSize());

        // Snapshot the user's Target Points NOW - before the order is placed - so a later
        // configuration change can never move THIS position's target (only new positions).
        BigDecimal targetPoints = fnoTradeConfigService.getEffectiveTargetPoints(userId, signal.getUnderlying());
        boolean targetEnabled = fnoTradeConfigService.isTargetEnabled(userId, signal.getUnderlying());

        OptionContract contract = optionContractResolver.resolve(signal.getUnderlying(), signal.getPrice(), params);
        auditService.record(AuditEventType.CONTRACT_RESOLVED, signal.getSignalId(), null,
                "tradingSymbol=" + contract.getTradingSymbol() + " strike=" + contract.getStrike()
                        + " expiry=" + contract.getExpiry() + " lotSize=" + contract.getLotSize()
                        + " lots=" + params.lots());

        int quantity = contract.getLotSize() * params.lots();

        RiskDecision decision = riskManagementService.evaluate(userId, signal.getSignalId(), signal.getUnderlying(), quantity, contract, true);
        if (!decision.isApproved()) {
            throw new RiskRejectedException(decision.getReasonCode(), decision.getReason());
        }

        String orderReferenceId = orderReferenceGenerator.generate(signal.getUnderlying());
        OrderEntity order = newOrderEntity(userId, signal, orderReferenceId, contract.getTradingSymbol(), TradingAction.BUY, quantity);
        order = orderRepository.save(order);

        dispatchOrder(userId, order, contract.getExchange(), signal.getPrice(), true);

        // Entry order is placed - hand it to the automatic profit-target monitor. It only starts
        // monitoring once Groww confirms a real filled price (never the signal price).
        if (!isRejected(order)) {
            try {
                positionTargetService.onEntryFilled(order, contract, targetPoints, targetEnabled);
            } catch (Exception ex) {
                log.error("TARGET_MONITOR_REGISTRATION_FAILED orderRef={} contract={}",
                        order.getOrderReferenceId(), contract.getTradingSymbol(), ex);
            }
        }
        return order;
    }

    // ------------------------------------------------------------------
    // Order dispatch (shared PAPER/LIVE path)
    // ------------------------------------------------------------------

    /**
     * Dispatches {@code order} to PAPER simulation or the live Groww API.
     *
     * <p>When {@code newEntry} is true this is the MANDATORY final guard:
     * {@link TradingSessionService#assertNewEntryAllowed()} is asserted
     * here, immediately before either {@code simulatePaperOrder} or the
     * Groww create-order call, so a signal that was accepted before 15:10
     * but reaches this point at/after 15:10 (async-queue / scheduler /
     * network delay, concurrent processing, races around the cutoff) never
     * opens a position. Exit / close orders pass {@code newEntry=false} and
     * are never blocked.
     */
    private void dispatchOrder(Long userId, OrderEntity order, String exchange, BigDecimal signalPrice, boolean newEntry) {
        if (newEntry) {
            tradingSessionService.assertNewEntryAllowed();
        }
        if (properties.getMode() == TradingMode.PAPER) {
            simulatePaperOrder(order);
        } else {
            submitLiveOrder(userId, order, exchange);
        }
        updateDailySummary(order.getAction(), isRejected(order));
    }

    private boolean isRejected(OrderEntity order) {
        return order.getStatus() == OrderStatus.REJECTED
                || order.getStatus() == OrderStatus.FAILED
                || order.getStatus() == OrderStatus.CANCELLED;
    }

    private void recordDispatchOutcome(TradingSignalEntity signal, Long userId, OrderEntity order) {
        boolean rejected = isRejected(order);
        upsertExecution(signal, userId, rejected ? SignalStatus.REJECTED : SignalStatus.EXECUTED,
                rejected ? "ORDER_" + order.getStatus() : null);
    }

    private void recordUserRejection(TradingSignalEntity signal, Long userId, String reasonCode, String message) {
        String reason = reasonCode + ": " + message;
        upsertExecution(signal, userId, SignalStatus.REJECTED, reason);
        log.info("SIGNAL_EXECUTION_REJECTED signalId={} userId={} reasonCode={} message={}",
                signal.getSignalId(), userId, reasonCode, message);
        auditService.record(AuditEventType.SIGNAL_REJECTED, signal.getSignalId(), null, "userId=" + userId + " " + reason);
        updateDailySummary(signal.getAction(), true);
    }

    private void recordUserFailure(TradingSignalEntity signal, Long userId, String message) {
        upsertExecution(signal, userId, SignalStatus.FAILED, message);
    }

    private void upsertExecution(TradingSignalEntity signal, Long userId, SignalStatus status, String reason) {
        SignalExecutionEntity execution = signalExecutionRepository.findBySignalIdAndUserId(signal.getSignalId(), userId)
                .orElseGet(() -> SignalExecutionEntity.forSignalAndUser(signal.getSignalId(), userId));
        execution.setStatus(status);
        execution.setRejectionReason(reason);
        signalExecutionRepository.save(execution);
    }

    private void finalizeSignal(TradingSignalEntity signal) {
        List<SignalExecutionEntity> executions = signalExecutionRepository.findBySignalId(signal.getSignalId());
        long executedCount = executions.stream().filter(e -> e.getStatus() == SignalStatus.EXECUTED).count();
        boolean anyFailed = executions.stream().anyMatch(e -> e.getStatus() == SignalStatus.FAILED);

        if (executedCount == executions.size()) {
            signal.setStatus(SignalStatus.EXECUTED);
            signal.setRejectionReason(null);
        } else if (executedCount == 0) {
            signal.setStatus(anyFailed ? SignalStatus.FAILED : SignalStatus.REJECTED);
            signal.setRejectionReason(executions.size() == 1
                    ? executions.get(0).getRejectionReason()
                    : executions.size() + " connected user(s) all failed - see signal_execution for per-user detail");
        } else {
            signal.setStatus(SignalStatus.PARTIALLY_EXECUTED);
            signal.setRejectionReason(executedCount + " of " + executions.size()
                    + " connected user(s) succeeded - see signal_execution for per-user detail");
        }
        signalRepository.save(signal);
    }

    private void simulatePaperOrder(OrderEntity order) {
        order.setGrowwOrderId("PAPER-" + order.getOrderReferenceId());
        order.setStatus(OrderStatus.COMPLETE);
        order.setFilledQuantity(order.getQuantity());
        // PAPER has no broker fill - the signal price IS the simulated fill, and the
        // automatic target monitor needs a non-null fill price to compute the target from.
        if (order.getAverageFillPrice() == null) {
            order.setAverageFillPrice(order.getPrice());
        }
        order.setBrokerRemark("Simulated fill (PAPER mode) - no order was sent to Groww");
        orderRepository.save(order);
        auditService.record(AuditEventType.PAPER_ORDER, order.getSignalId(), order.getOrderReferenceId(),
                "tradingSymbol=" + order.getTradingSymbol() + " action=" + order.getAction() + " quantity=" + order.getQuantity());
    }

    private void submitLiveOrder(Long userId, OrderEntity order, String exchange) {
        finalLiveSafetyCheck(userId);
        OrderEntity submitted = orderExecutionSupport.submitAndReconcile(userId, order, exchange);
        auditService.record(AuditEventType.LIVE_ORDER_SUBMITTED, submitted.getSignalId(), submitted.getOrderReferenceId(),
                "growwOrderId=" + submitted.getGrowwOrderId() + " status=" + submitted.getStatus());
    }

    private void finalLiveSafetyCheck(Long userId) {
        if (!growwAuthenticationService.isAuthenticated(userId)) {
            throw new AuthenticationRequiredException("Groww is not authenticated - refusing to place a live order");
        }
        if (tradingStateService.isKillSwitchEnabled()) {
            throw new OrderRejectedException("Kill switch is enabled - refusing to place a live order");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private OrderEntity newOrderEntity(Long userId, TradingSignalEntity signal, String orderReferenceId, String tradingSymbol,
                                        TradingAction action, int quantity) {
        OrderEntity order = new OrderEntity();
        order.setSignalId(signal.getSignalId());
        order.setUserId(userId);
        order.setOrderReferenceId(orderReferenceId);
        order.setUnderlying(signal.getUnderlying());
        order.setTradingSymbol(tradingSymbol);
        order.setAction(action);
        order.setQuantity(quantity);
        order.setPrice(signal.getPrice());
        order.setOrderType(properties.getOrder().getOrderType());
        order.setProduct(properties.getOrder().getProduct());
        order.setSegment(SEGMENT_FNO);
        order.setStatus(OrderStatus.PENDING);
        order.setFilledQuantity(0);
        return order;
    }

    private void markValidated(TradingSignalEntity signal) {
        signal.setStatus(SignalStatus.VALIDATED);
        signalRepository.save(signal);
        auditService.record(AuditEventType.SIGNAL_VALIDATED, signal.getSignalId(), null, "session window open, trading allowed");
    }

    private void rejectSignal(TradingSignalEntity signal, String reasonCode, String message) {
        signal.setStatus(SignalStatus.REJECTED);
        signal.setRejectionReason(reasonCode + ": " + message);
        signalRepository.save(signal);
        log.info("SIGNAL_REJECTED signalId={} reasonCode={} message={}", signal.getSignalId(), reasonCode, message);
        auditService.record(AuditEventType.SIGNAL_REJECTED, signal.getSignalId(), null, reasonCode + ": " + message);
        updateDailySummary(signal.getAction(), true);
    }

    private void markFailed(TradingSignalEntity signal, String message) {
        signal.setStatus(SignalStatus.FAILED);
        signal.setRejectionReason(message);
        signalRepository.save(signal);
    }

    private void updateDailySummary(TradingAction action, boolean rejected) {
        try {
            LocalDate today = LocalDate.now(properties.getZoneId());
            DailyTradingSummaryEntity summary = dailySummaryRepository.findByTradingDate(today)
                    .orElseGet(() -> DailyTradingSummaryEntity.forDate(today));
            summary.setOrdersCount(summary.getOrdersCount() + (rejected ? 0 : 1));
            if (!rejected) {
                if (action == TradingAction.BUY) {
                    summary.setBuyCount(summary.getBuyCount() + 1);
                } else {
                    summary.setSellCount(summary.getSellCount() + 1);
                }
            } else {
                summary.setRejectedCount(summary.getRejectedCount() + 1);
            }
            dailySummaryRepository.save(summary);
        } catch (Exception ex) {
            log.warn("DAILY_SUMMARY_UPDATE_FAILED reason={}", ex.getMessage());
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }
}
