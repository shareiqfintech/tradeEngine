package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.OptionContract;
import com.example.trading.dto.Position;
import com.example.trading.dto.RiskDecision;
import com.example.trading.enums.AuditEventType;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.repository.DailyTradingSummaryRepository;
import com.example.trading.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The final gate before an order (paper or live) is placed. Every check
 * below produces a specific {@code reasonCode} so a REJECTED decision is
 * always actionable from the audit trail, not just "risk said no".
 */
@Slf4j
@Service
public class RiskManagementService {

    private final TradingProperties properties;
    private final TradingStateService tradingStateService;
    private final GrowwAuthenticationService growwAuthenticationService;
    private final MarketHoursService marketHoursService;
    private final OrderRepository orderRepository;
    private final DailyTradingSummaryRepository dailyTradingSummaryRepository;
    private final GrowwPositionService growwPositionService;
    private final AuditService auditService;

    public RiskManagementService(TradingProperties properties,
                                  TradingStateService tradingStateService,
                                  GrowwAuthenticationService growwAuthenticationService,
                                  MarketHoursService marketHoursService,
                                  OrderRepository orderRepository,
                                  DailyTradingSummaryRepository dailyTradingSummaryRepository,
                                  GrowwPositionService growwPositionService,
                                  AuditService auditService) {
        this.properties = properties;
        this.tradingStateService = tradingStateService;
        this.growwAuthenticationService = growwAuthenticationService;
        this.marketHoursService = marketHoursService;
        this.orderRepository = orderRepository;
        this.dailyTradingSummaryRepository = dailyTradingSummaryRepository;
        this.growwPositionService = growwPositionService;
        this.auditService = auditService;
    }

    /**
     * @param isOpeningNewPosition true for BUY (opens/adds to a position); false for SELL (closes one)
     */
    public RiskDecision evaluate(Long userId, String signalId, String underlying, int quantity,
                                  OptionContract contract, boolean isOpeningNewPosition) {
        RiskDecision decision = evaluateInternal(userId, signalId, underlying, quantity, contract, isOpeningNewPosition);

        AuditEventType eventType = decision.isApproved() ? AuditEventType.RISK_APPROVED : AuditEventType.RISK_REJECTED;
        String details = decision.isApproved()
                ? "underlying=" + underlying + " quantity=" + quantity
                : "reasonCode=" + decision.getReasonCode() + " reason=" + decision.getReason();
        auditService.record(eventType, signalId, null, details);
        return decision;
    }

    private RiskDecision evaluateInternal(Long userId, String signalId, String underlying, int quantity,
                                           OptionContract contract, boolean isOpeningNewPosition) {
        if (tradingStateService.isKillSwitchEnabled()) {
            return RiskDecision.rejected("KILL_SWITCH_ENABLED", "Kill switch is enabled - no new orders");
        }
        if (tradingStateService.isPaused()) {
            return RiskDecision.rejected("TRADING_PAUSED", "Trading is paused");
        }
        if (!tradingStateService.isTradingWindowOpen()) {
            return RiskDecision.rejected("TRADING_WINDOW_CLOSED", "Trading window is not open");
        }
        if (!marketHoursService.isMarketOpenNow()) {
            return RiskDecision.rejected("MARKET_CLOSED", "Market is closed");
        }
        if (!growwAuthenticationService.isAuthenticated(userId)) {
            return RiskDecision.rejected("AUTH_REQUIRED", "Groww is not authenticated");
        }
        if (isOpeningNewPosition && (contract == null || contract.getLotSize() <= 0 || contract.getTradingSymbol() == null)) {
            return RiskDecision.rejected("INVALID_CONTRACT", "Resolved option contract is invalid");
        }

        int maxQuantity = properties.getRisk().getMaxQuantity();
        if (quantity <= 0 || quantity > maxQuantity) {
            return RiskDecision.rejected("QUANTITY_LIMIT_EXCEEDED",
                    "Quantity " + quantity + " exceeds max-quantity=" + maxQuantity);
        }

        long ordersToday = ordersPlacedToday();
        int maxOrdersPerDay = properties.getRisk().getMaxOrdersPerDay();
        if (ordersToday >= maxOrdersPerDay) {
            return RiskDecision.rejected("MAX_ORDERS_PER_DAY_EXCEEDED",
                    "Already placed " + ordersToday + " orders today (max=" + maxOrdersPerDay + ")");
        }

        long ordersForSignal = orderRepository.countBySignalIdAndCreatedAtBetween(signalId, startOfDay(), endOfDay());
        int maxOrdersPerSignal = properties.getRisk().getMaxOrdersPerSignal();
        if (ordersForSignal >= maxOrdersPerSignal) {
            return RiskDecision.rejected("MAX_ORDERS_PER_SIGNAL_EXCEEDED",
                    "Signal " + signalId + " already has " + ordersForSignal + " order(s)");
        }

        if (isOpeningNewPosition) {
            long openPositions = countOpenPositions(userId);
            int maxOpenPositions = properties.getRisk().getMaxOpenPositions();
            if (openPositions >= maxOpenPositions) {
                return RiskDecision.rejected("MAX_OPEN_POSITIONS_EXCEEDED",
                        "Already " + openPositions + " open position(s) (max=" + maxOpenPositions + ")");
            }
        }

        BigDecimal dailyLoss = currentDailyLoss();
        double maxDailyLoss = properties.getRisk().getMaxDailyLoss();
        if (dailyLoss.compareTo(BigDecimal.valueOf(maxDailyLoss)) >= 0) {
            return RiskDecision.rejected("MAX_DAILY_LOSS_EXCEEDED",
                    "Today's realized loss " + dailyLoss + " has reached the limit " + maxDailyLoss);
        }

        return RiskDecision.approved();
    }

    /**
     * Read-only snapshot of today's usage against the configured limits -
     * the exact same counters {@link #evaluateInternal} checks against, so
     * a reporting endpoint (e.g. {@code GET /api/trading/risk}) never drifts
     * out of sync with what risk evaluation actually enforces.
     */
    public RiskUsageSnapshot getUsageSnapshot(Long userId) {
        return new RiskUsageSnapshot(
                properties.getRisk().getMaxOrdersPerDay(), ordersPlacedToday(),
                properties.getRisk().getMaxOpenPositions(), safeCountOpenPositions(userId),
                properties.getRisk().getMaxQuantity(),
                properties.getRisk().getMaxDailyLoss(), currentDailyLoss().doubleValue(),
                properties.getRisk().getMaxOrdersPerSignal(), properties.isAllowShortSelling());
    }

    /**
     * Same broker call as {@link #countOpenPositions(Long)}, but degrades to
     * 0 instead of throwing - this is a read-only reporting snapshot (GET
     * /api/trading/risk), not a live risk decision, so a Groww outage
     * should not take the whole endpoint down (mirrors
     * TradingAdminController#openPositions()'s identical defensive pattern).
     */
    private long safeCountOpenPositions(Long userId) {
        try {
            return countOpenPositions(userId);
        } catch (Exception ex) {
            log.warn("RISK_SNAPSHOT_OPEN_POSITIONS_LOOKUP_FAILED reason={}", ex.getMessage());
            return 0;
        }
    }

    public record RiskUsageSnapshot(int maxOrdersPerDay, long ordersToday,
                                     int maxOpenPositions, long openPositions,
                                     int maxQuantity,
                                     double maxDailyLoss, double dailyLossSoFar,
                                     int maxOrdersPerSignal, boolean allowShortSelling) {
    }

    private long ordersPlacedToday() {
        return orderRepository.countByCreatedAtBetween(startOfDay(), endOfDay());
    }

    private long countOpenPositions(Long userId) {
        List<Position> positions = growwPositionService.getPositions(userId);
        return positions.stream().filter(p -> p.getNetQuantity() != null && p.getNetQuantity() != 0).count();
    }

    private BigDecimal currentDailyLoss() {
        LocalDate today = LocalDate.now(properties.getZoneId());
        return dailyTradingSummaryRepository.findByTradingDate(today)
                .map(summary -> summary.getRealizedPnl().signum() < 0
                        ? summary.getRealizedPnl().abs()
                        : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    private Instant startOfDay() {
        ZoneId zone = properties.getZoneId();
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    private Instant endOfDay() {
        ZoneId zone = properties.getZoneId();
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
    }
}
