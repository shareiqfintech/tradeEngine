package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.OptionContract;
import com.example.trading.dto.Position;
import com.example.trading.dto.RiskDecision;
import com.example.trading.entity.DailyTradingSummaryEntity;
import com.example.trading.enums.OptionType;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.repository.DailyTradingSummaryRepository;
import com.example.trading.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskManagementServiceTest {

    private static final Long USER_ID = 1L;

    private TradingProperties properties;
    private TradingStateService tradingStateService;
    private GrowwAuthenticationService growwAuthenticationService;
    private MarketHoursService marketHoursService;
    private OrderRepository orderRepository;
    private DailyTradingSummaryRepository dailyTradingSummaryRepository;
    private GrowwPositionService growwPositionService;
    private AuditService auditService;
    private RiskManagementService service;

    private final OptionContract validContract = OptionContract.builder()
            .tradingSymbol("NIFTY25SEP25000CE").lotSize(75).exchange("NSE").segment("FNO")
            .optionType(OptionType.CE).strike(BigDecimal.valueOf(25000)).expiry(LocalDate.now())
            .buyAllowed(true).sellAllowed(true).build();

    @BeforeEach
    void setUp() {
        properties = new TradingProperties();
        tradingStateService = mock(TradingStateService.class);
        growwAuthenticationService = mock(GrowwAuthenticationService.class);
        marketHoursService = mock(MarketHoursService.class);
        orderRepository = mock(OrderRepository.class);
        dailyTradingSummaryRepository = mock(DailyTradingSummaryRepository.class);
        growwPositionService = mock(GrowwPositionService.class);
        auditService = mock(AuditService.class);

        service = new RiskManagementService(properties, tradingStateService, growwAuthenticationService,
                marketHoursService, orderRepository, dailyTradingSummaryRepository, growwPositionService, auditService);

        // Happy-path defaults; individual tests override what they need to violate.
        when(tradingStateService.isKillSwitchEnabled()).thenReturn(false);
        when(tradingStateService.isPaused()).thenReturn(false);
        when(tradingStateService.isTradingWindowOpen()).thenReturn(true);
        when(marketHoursService.isMarketOpenNow()).thenReturn(true);
        when(growwAuthenticationService.isAuthenticated(USER_ID)).thenReturn(true);
        when(orderRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(orderRepository.countBySignalIdAndCreatedAtBetween(anyString(), any(), any())).thenReturn(0L);
        when(dailyTradingSummaryRepository.findByTradingDate(any())).thenReturn(Optional.empty());
        when(growwPositionService.getPositions(USER_ID)).thenReturn(List.of());
    }

    @Test
    void approves_whenEverythingIsFine() {
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isTrue();
    }

    @Test
    void rejects_whenKillSwitchEnabled() {
        when(tradingStateService.isKillSwitchEnabled()).thenReturn(true);
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("KILL_SWITCH_ENABLED");
    }

    @Test
    void rejects_whenPaused() {
        when(tradingStateService.isPaused()).thenReturn(true);
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("TRADING_PAUSED");
    }

    @Test
    void rejects_whenMarketClosed() {
        when(marketHoursService.isMarketOpenNow()).thenReturn(false);
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MARKET_CLOSED");
    }

    @Test
    void rejects_whenNotAuthenticated() {
        when(growwAuthenticationService.isAuthenticated(USER_ID)).thenReturn(false);
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("AUTH_REQUIRED");
    }

    @Test
    void rejects_whenQuantityExceedsMax() {
        properties.getRisk().setMaxQuantity(50);
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("QUANTITY_LIMIT_EXCEEDED");
    }

    @Test
    void rejects_whenMaxOrdersPerDayReached() {
        properties.getRisk().setMaxOrdersPerDay(5);
        when(orderRepository.countByCreatedAtBetween(any(), any())).thenReturn(5L);
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MAX_ORDERS_PER_DAY_EXCEEDED");
    }

    @Test
    void rejects_whenMaxOpenPositionsReachedOnBuy() {
        properties.getRisk().setMaxOpenPositions(1);
        Position openPosition = Position.builder().tradingSymbol("NIFTY25SEP24900CE").netQuantity(75).build();
        when(growwPositionService.getPositions(USER_ID)).thenReturn(List.of(openPosition));

        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MAX_OPEN_POSITIONS_EXCEEDED");
    }

    @Test
    void sellDoesNotCheckMaxOpenPositions() {
        properties.getRisk().setMaxOpenPositions(0);
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, null, false);
        assertThat(decision.isApproved()).isTrue();
    }

    @Test
    void rejects_whenDailyLossLimitReached() {
        properties.getRisk().setMaxDailyLoss(1000);
        DailyTradingSummaryEntity summary = DailyTradingSummaryEntity.forDate(LocalDate.now());
        summary.setRealizedPnl(BigDecimal.valueOf(-1500));
        when(dailyTradingSummaryRepository.findByTradingDate(any())).thenReturn(Optional.of(summary));

        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MAX_DAILY_LOSS_EXCEEDED");
    }

    @Test
    void rejects_whenContractInvalidOnBuy() {
        OptionContract invalid = OptionContract.builder().tradingSymbol(null).lotSize(0).build();
        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, invalid, true);
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("INVALID_CONTRACT");
    }

    @Test
    void userBsOpenPositions_neverCountTowardUserAsMaxOpenPositionsCheck() {
        properties.getRisk().setMaxOpenPositions(1);
        // User A has no open positions of their own...
        when(growwPositionService.getPositions(USER_ID)).thenReturn(List.of());
        // ...even though a completely different user (never passed to evaluate() here) has plenty.
        Long userB = 2L;
        when(growwPositionService.getPositions(userB)).thenReturn(List.of(
                Position.builder().tradingSymbol("NIFTY25SEP24900CE").netQuantity(75).build()));

        RiskDecision decision = service.evaluate(USER_ID, "SIG-1", "NIFTY", 75, validContract, true);

        assertThat(decision.isApproved()).isTrue();
    }
}
