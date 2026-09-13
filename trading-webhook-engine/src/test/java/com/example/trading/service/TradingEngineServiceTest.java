package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.FnoResolutionParams;
import com.example.trading.dto.OptionContract;
import com.example.trading.dto.Position;
import com.example.trading.dto.RiskDecision;
import com.example.trading.entity.OrderEntity;
import com.example.trading.entity.SignalExecutionEntity;
import com.example.trading.entity.TradingSignalEntity;
import com.example.trading.enums.*;
import com.example.trading.exception.GrowwApiException;
import com.example.trading.exception.TradingSessionClosedException;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.groww.dto.GrowwOrderResponse;
import com.example.trading.redis.DistributedLockService;
import com.example.trading.repository.DailyTradingSummaryRepository;
import com.example.trading.repository.OrderRepository;
import com.example.trading.repository.SignalExecutionRepository;
import com.example.trading.repository.TradingSignalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the full {@code TradingEngineService.processSignal} pipeline
 * with every collaborator mocked - this is where the BUY/SELL business
 * rules from the project spec are actually verified end-to-end (minus the
 * real network/database, which are covered by the narrower per-component
 * tests elsewhere in this package). {@code USER_ID} is the sole connected
 * user {@code GrowwUserResolver} reports for every single-user test in this
 * class (see setUp); the multi-user fan-out section further down exercises
 * {@code USER_A}/{@code USER_B}/{@code USER_C} together.
 */
class TradingEngineServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long USER_C = 3L;

    private TradingSignalRepository signalRepository;
    private SignalExecutionRepository signalExecutionRepository;
    private OrderRepository orderRepository;
    private DailyTradingSummaryRepository dailySummaryRepository;
    private TradingStateService tradingStateService;
    private TradingSessionService tradingSessionService;
    private GrowwAuthenticationService growwAuthenticationService;
    private PositionService positionService;
    private OptionContractResolver optionContractResolver;
    private FnoTradeConfigService fnoTradeConfigService;
    private RiskManagementService riskManagementService;
    private OrderReferenceGenerator orderReferenceGenerator;
    private GrowwOrderStatusService growwOrderStatusService;
    private GrowwApiClient growwApiClient;
    private OrderExecutionSupport orderExecutionSupport;
    private PositionTargetService positionTargetService;
    private AuditService auditService;
    private DistributedLockService distributedLockService;
    private TradingProperties properties;
    private GrowwUserResolver growwUserResolver;
    private TradingEngineService engine;
    private Map<String, SignalExecutionEntity> executionStore;

    private final OptionContract contract = OptionContract.builder()
            .tradingSymbol("NIFTY25SEP25000CE").lotSize(75).exchange("NSE").segment("FNO")
            .optionType(OptionType.CE).strike(BigDecimal.valueOf(25000)).buyAllowed(true).sellAllowed(true).build();

    @BeforeEach
    void setUp() {
        signalRepository = mock(TradingSignalRepository.class);
        signalExecutionRepository = mock(SignalExecutionRepository.class);
        orderRepository = mock(OrderRepository.class);
        dailySummaryRepository = mock(DailyTradingSummaryRepository.class);
        tradingStateService = mock(TradingStateService.class);
        tradingSessionService = mock(TradingSessionService.class);
        growwAuthenticationService = mock(GrowwAuthenticationService.class);
        positionService = mock(PositionService.class);
        optionContractResolver = mock(OptionContractResolver.class);
        fnoTradeConfigService = mock(FnoTradeConfigService.class);
        riskManagementService = mock(RiskManagementService.class);
        orderReferenceGenerator = mock(OrderReferenceGenerator.class);
        growwOrderStatusService = mock(GrowwOrderStatusService.class);
        growwApiClient = mock(GrowwApiClient.class);
        auditService = mock(AuditService.class);
        distributedLockService = mock(DistributedLockService.class);
        properties = new TradingProperties();
        growwUserResolver = mock(GrowwUserResolver.class);
        positionTargetService = mock(PositionTargetService.class);

        // Real collaborator wired with the already-mocked lower-level deps, so
        // the LIVE-path tests still assert directly on growwApiClient /
        // growwOrderStatusService interactions.
        orderExecutionSupport = new OrderExecutionSupport(growwApiClient, growwOrderStatusService,
                growwAuthenticationService, auditService, orderRepository, properties);

        engine = new TradingEngineService(signalRepository, signalExecutionRepository, orderRepository, dailySummaryRepository,
                tradingStateService, tradingSessionService, growwAuthenticationService, positionService,
                optionContractResolver, fnoTradeConfigService, riskManagementService, orderReferenceGenerator,
                orderExecutionSupport, positionTargetService, auditService, distributedLockService, properties,
                growwUserResolver);

        // In-memory fake for SignalExecutionRepository: TradingEngineService
        // writes one row per (signalId, userId) during the fan-out loop and
        // reads them all back once, in finalizeSignal, to compute the
        // signal's own aggregate status - a plain mock can't round-trip
        // that, so back it with a real map.
        executionStore = new HashMap<>();
        when(signalExecutionRepository.findBySignalIdAndUserId(anyString(), anyLong())).thenAnswer(inv ->
                Optional.ofNullable(executionStore.get(inv.getArgument(0) + "|" + inv.getArgument(1))));
        when(signalExecutionRepository.save(any(SignalExecutionEntity.class))).thenAnswer(inv -> {
            SignalExecutionEntity execution = inv.getArgument(0);
            executionStore.put(execution.getSignalId() + "|" + execution.getUserId(), execution);
            return execution;
        });
        when(signalExecutionRepository.findBySignalId(anyString())).thenAnswer(inv -> {
            String signalId = inv.getArgument(0);
            return executionStore.values().stream().filter(e -> e.getSignalId().equals(signalId)).collect(Collectors.toList());
        });

        // Default: exactly one connected user (USER_ID) - a TradingView
        // webhook has no session of its own, so this is how the engine
        // learns which users' Groww accounts to fan the signal out to. See
        // the "zero connected users" test and the multi-user fan-out
        // section further down for N != 1.
        when(growwUserResolver.findAllConnectedUserIds()).thenReturn(List.of(USER_ID));
        when(tradingStateService.isTradingAllowed()).thenReturn(true);
        when(growwAuthenticationService.isAuthenticated(USER_ID)).thenReturn(true);
        when(distributedLockService.tryLock(anyString(), any())).thenReturn(Optional.of("lock-token"));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReferenceGenerator.generate(anyString())).thenReturn("TV-NIFTY-ABC12345");
        when(dailySummaryRepository.findByTradingDate(any())).thenReturn(Optional.empty());
        when(fnoTradeConfigService.getEffectiveParams(anyLong(), anyString()))
                .thenReturn(new FnoResolutionParams("CE", "NEAREST", "ATM", 0, 1, null));
        when(fnoTradeConfigService.getEffectiveTargetPoints(anyLong(), anyString())).thenReturn(new BigDecimal("8"));
        when(fnoTradeConfigService.isTargetEnabled(anyLong(), anyString())).thenReturn(true);
        // Default: no opposite-side position held, so the close-and-switch
        // step is a no-op and every existing BUY/SELL test below behaves
        // exactly as a plain open, unless a test overrides this to
        // simulate an existing opposite position.
        when(positionService.findActivePosition(any(), anyString(), any(OptionType.class), anyString()))
                .thenReturn(Optional.empty());
    }

    private TradingSignalEntity buySignal() {
        TradingSignalEntity entity = TradingSignalEntity.received("NIFTY-001", TradingAction.BUY, "NIFTY", "NSE",
                "15m", BigDecimal.valueOf(25000.50), Instant.now());
        entity.setStatus(SignalStatus.RECEIVED);
        return entity;
    }

    private TradingSignalEntity sellSignal() {
        TradingSignalEntity entity = TradingSignalEntity.received("NIFTY-002", TradingAction.SELL, "NIFTY", "NSE",
                "15m", BigDecimal.valueOf(25020.00), Instant.now());
        entity.setStatus(SignalStatus.RECEIVED);
        return entity;
    }

    @Test
    void buySignal_paperMode_executesAndSimulatesOrder() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        verifyNoInteractions(growwApiClient); // PAPER mode must NEVER call Groww's order API
        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        OrderEntity savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETE);
        assertThat(savedOrder.getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
        verify(auditService).record(eq(AuditEventType.PAPER_ORDER), any(), any(), any());
    }

    @Test
    void buySignal_riskRejected_neverPlacesOrder() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(anyString(), any(), any())).thenReturn(contract);
        when(riskManagementService.evaluate(any(), any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(RiskDecision.rejected("MAX_ORDERS_PER_DAY_EXCEEDED", "too many orders"));

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("MAX_ORDERS_PER_DAY_EXCEEDED");
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void newEntryAfterCutoff_earlyGuardRejectsWholeSignal_noOrderNoGroww() {
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        doThrow(new TradingSessionClosedException(TradingSessionClosedException.SESSION_TRADING_CUTOFF,
                "New entries are blocked at/after the 15:10 IST safety cutoff"))
                .when(tradingSessionService).assertNewEntryAllowed();

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("SESSION_TRADING_CUTOFF");
        verifyNoInteractions(riskManagementService);
        verifyNoInteractions(growwApiClient);
        verify(orderRepository, never()).save(any());
        verify(auditService).record(eq(AuditEventType.NEW_ENTRY_REJECTED_TRADING_CUTOFF), eq("NIFTY-001"), any(), any());
    }

    @Test
    void newEntryBeforeSessionStart_earlyGuardRejects_withSessionNotStartedReason() {
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        doThrow(new TradingSessionClosedException(TradingSessionClosedException.SESSION_NOT_STARTED,
                "New entries are not allowed before 09:25 IST"))
                .when(tradingSessionService).assertNewEntryAllowed();

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("SESSION_NOT_STARTED");
        verify(auditService).record(eq(AuditEventType.NEW_ENTRY_REJECTED_SESSION_NOT_STARTED), eq("NIFTY-001"), any(), any());
    }

    @Test
    void delayedSignal_crossesCutoffAtFinalDispatchGuard_paperMode_neverSimulatesEntry() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(any(), any(), any(), anyInt(), any(), anyBoolean())).thenReturn(RiskDecision.approved());
        // Accepted at processSignal start (1st call passes); 15:10 crossed by the
        // time the new-entry order is dispatched (2nd call throws).
        doNothing().doThrow(new TradingSessionClosedException(TradingSessionClosedException.SESSION_TRADING_CUTOFF,
                "cutoff crossed mid-execution"))
                .when(tradingSessionService).assertNewEntryAllowed();

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("SESSION_TRADING_CUTOFF");
        verify(auditService, never()).record(eq(AuditEventType.PAPER_ORDER), any(), any(), any());
        verifyNoInteractions(growwApiClient);
    }

    @Test
    void delayedSignal_crossesCutoffAtFinalDispatchGuard_liveMode_neverCallsGroww() {
        properties.setMode(TradingMode.LIVE);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(any(), any(), any(), anyInt(), any(), anyBoolean())).thenReturn(RiskDecision.approved());
        doNothing().doThrow(new TradingSessionClosedException(TradingSessionClosedException.SESSION_TRADING_CUTOFF,
                "cutoff crossed mid-execution"))
                .when(tradingSessionService).assertNewEntryAllowed();

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("SESSION_TRADING_CUTOFF");
        verifyNoInteractions(growwApiClient);
    }

    @Test
    void buySignal_tradingNotAllowed_killSwitchReason_rejectsBeforeAuth() {
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(tradingStateService.isTradingAllowed()).thenReturn(false);
        when(tradingStateService.isKillSwitchEnabled()).thenReturn(true);

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("KILL_SWITCH_ENABLED");
        verifyNoInteractions(growwAuthenticationService);
    }

    @Test
    void buySignal_noConnectedGrowwUser_rejectsWithoutTouchingAuthOrRisk() {
        when(growwUserResolver.findAllConnectedUserIds()).thenReturn(List.of());
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("GROWW_NOT_CONNECTED");
        verifyNoInteractions(growwAuthenticationService);
        verifyNoInteractions(riskManagementService);
    }

    @Test
    void buySignal_notAuthenticated_andReauthFails_rejectsAuthRequired() {
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(growwAuthenticationService.isAuthenticated(USER_ID)).thenReturn(false);
        when(growwAuthenticationService.authenticate(USER_ID)).thenReturn(false);

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("AUTH_REQUIRED");
        verifyNoInteractions(optionContractResolver);
    }

    // ------------------------------------------------------------------
    // Signal-driven position switching (BULLISH closes PE then buys CE;
    // BEARISH closes CE then buys PE) - test numbering matches the project
    // spec's section 20 exactly, for traceability against that document.
    // ------------------------------------------------------------------

    private Position openPosition(String tradingSymbol, int netQuantity) {
        return Position.builder().tradingSymbol(tradingSymbol).exchange("NSE").product("NRML")
                .netQuantity(netQuantity).build();
    }

    private OptionContract peContract(String tradingSymbol, int lotSize) {
        return OptionContract.builder().tradingSymbol(tradingSymbol).lotSize(lotSize).exchange("NSE").segment("FNO")
                .optionType(OptionType.PE).strike(BigDecimal.valueOf(25000)).buyAllowed(true).sellAllowed(true).build();
    }

    /** Bullish switch: existing PE of {@code existingPeQuantity} closed in full, then the nearest CE (lot size 75, 1 lot = 75) bought. */
    private void assertBullishSwitch(int existingPeQuantity) {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));

        Position existingPe = openPosition("NIFTY25SEP25000PE", existingPeQuantity);
        when(positionService.findActivePosition(USER_ID, "NIFTY", OptionType.PE, "NIFTY-001")).thenReturn(Optional.of(existingPe));
        when(positionService.availableQuantityToSell(existingPe)).thenReturn(existingPeQuantity);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-001"), eq("NIFTY"), eq(existingPeQuantity), isNull(), eq(false)))
                .thenReturn(RiskDecision.approved());
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeast(2)).save(captor.capture());
        OrderEntity closeOrder = captor.getAllValues().stream().filter(o -> o.getAction() == TradingAction.SELL).findFirst().orElseThrow();
        OrderEntity openOrder = captor.getAllValues().stream().filter(o -> o.getAction() == TradingAction.BUY).findFirst().orElseThrow();
        assertThat(closeOrder.getTradingSymbol()).isEqualTo("NIFTY25SEP25000PE");
        assertThat(closeOrder.getQuantity()).isEqualTo(existingPeQuantity);
        assertThat(openOrder.getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
        assertThat(openOrder.getQuantity()).isEqualTo(75);
        verify(auditService).record(eq(AuditEventType.POSITION_CLOSE_VERIFIED), any(), any(), any());
    }

    /** Bearish switch: existing CE of {@code existingCeQuantity} closed in full, then the nearest PE (lot size 75, 1 lot = 75) bought. */
    private void assertBearishSwitch(int existingCeQuantity) {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = sellSignal();
        when(signalRepository.findBySignalId("NIFTY-002")).thenReturn(Optional.of(signal));

        Position existingCe = openPosition("NIFTY25SEP25000CE", existingCeQuantity);
        when(positionService.findActivePosition(USER_ID, "NIFTY", OptionType.CE, "NIFTY-002")).thenReturn(Optional.of(existingCe));
        when(positionService.availableQuantityToSell(existingCe)).thenReturn(existingCeQuantity);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-002"), eq("NIFTY"), eq(existingCeQuantity), isNull(), eq(false)))
                .thenReturn(RiskDecision.approved());
        OptionContract pe = peContract("NIFTY25SEP25000PE", 75);
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(pe);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-002"), eq("NIFTY"), eq(75), eq(pe), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-002");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeast(2)).save(captor.capture());
        OrderEntity closeOrder = captor.getAllValues().stream().filter(o -> o.getAction() == TradingAction.SELL).findFirst().orElseThrow();
        OrderEntity openOrder = captor.getAllValues().stream().filter(o -> o.getAction() == TradingAction.BUY).findFirst().orElseThrow();
        assertThat(closeOrder.getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
        assertThat(closeOrder.getQuantity()).isEqualTo(existingCeQuantity);
        assertThat(openOrder.getTradingSymbol()).isEqualTo("NIFTY25SEP25000PE");
        assertThat(openOrder.getQuantity()).isEqualTo(75);
        verify(auditService).record(eq(AuditEventType.POSITION_CLOSE_VERIFIED), any(), any(), any());
    }

    @Test
    void test1_existingPe65_bullish_sellsExactQuantityThenBuysNearestCe() {
        assertBullishSwitch(65);
    }

    @Test
    void test2_existingPe130_bullish_sellsExactQuantityThenBuysNearestCe() {
        assertBullishSwitch(130);
    }

    @Test
    void test3_existingPe260_bullish_sellsExactQuantityThenBuysNearestCe() {
        assertBullishSwitch(260);
    }

    @Test
    void test4_existingCe195_bearish_sellsExactQuantityThenBuysNearestPe() {
        assertBearishSwitch(195);
    }

    @Test
    void test5_existingCe325_bearish_sellsExactQuantityThenBuysNearestPe() {
        assertBearishSwitch(325);
    }

    @Test
    void test6_existingPe260_onlyPartiallyFilledOnClose_doesNotBuyCe() {
        properties.setMode(TradingMode.LIVE);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        Position existingPe = openPosition("NIFTY25SEP25000PE", 260);
        when(positionService.findActivePosition(USER_ID, "NIFTY", OptionType.PE, "NIFTY-001")).thenReturn(Optional.of(existingPe));
        when(positionService.availableQuantityToSell(existingPe)).thenReturn(260);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-001"), eq("NIFTY"), eq(260), isNull(), eq(false)))
                .thenReturn(RiskDecision.approved());

        GrowwOrderResponse closeResponse = new GrowwOrderResponse();
        closeResponse.setGrowwOrderId("GID-CLOSE");
        closeResponse.setOrderStatus("OPEN");
        closeResponse.setFilledQuantity(130); // only half of the requested 260 filled
        when(growwApiClient.createOrder(eq(USER_ID), any())).thenReturn(closeResponse);

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("POSITION_CLOSE_INCOMPLETE");
        verify(growwApiClient, times(1)).createOrder(eq(USER_ID), any()); // only the close attempt - never a CE buy
        verifyNoInteractions(optionContractResolver);
        verify(auditService).record(eq(AuditEventType.POSITION_CLOSE_INCOMPLETE), any(), any(), any());
    }

    @Test
    void test7_existingCe130_closeSellFails_doesNotBuyPe() {
        properties.setMode(TradingMode.LIVE);
        TradingSignalEntity signal = sellSignal();
        when(signalRepository.findBySignalId("NIFTY-002")).thenReturn(Optional.of(signal));
        Position existingCe = openPosition("NIFTY25SEP25000CE", 130);
        when(positionService.findActivePosition(USER_ID, "NIFTY", OptionType.CE, "NIFTY-002")).thenReturn(Optional.of(existingCe));
        when(positionService.availableQuantityToSell(existingCe)).thenReturn(130);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-002"), eq("NIFTY"), eq(130), isNull(), eq(false)))
                .thenReturn(RiskDecision.approved());
        when(growwApiClient.createOrder(eq(USER_ID), any())).thenThrow(GrowwApiException.apiError("Invalid trading symbol", "GA001"));

        engine.processSignal("NIFTY-002");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("ORDER_REJECTED");
        verifyNoInteractions(optionContractResolver);
    }

    @Test
    void test8_noExistingPe_bullish_buysNearestCeDirectly() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        verify(riskManagementService, never()).evaluate(any(), any(), any(), anyInt(), isNull(), eq(false));
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(o -> assertThat(o.getAction()).isEqualTo(TradingAction.BUY));
    }

    @Test
    void test9_noExistingCe_bearish_buysNearestPeDirectly() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = sellSignal();
        when(signalRepository.findBySignalId("NIFTY-002")).thenReturn(Optional.of(signal));
        OptionContract pe = peContract("NIFTY25SEP25000PE", 75);
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(pe);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-002"), eq("NIFTY"), eq(75), eq(pe), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-002");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        verify(riskManagementService, never()).evaluate(any(), any(), any(), anyInt(), isNull(), eq(false));
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(o -> assertThat(o.getAction()).isEqualTo(TradingAction.BUY));
        assertThat(captor.getValue().getTradingSymbol()).isEqualTo("NIFTY25SEP25000PE");
    }

    @Test
    void test10_existingPe260_newCeQuantity130_oldAndNewQuantitiesAreIndependent() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));

        Position existingPe = openPosition("NIFTY25SEP25000PE", 260);
        when(positionService.findActivePosition(USER_ID, "NIFTY", OptionType.PE, "NIFTY-001")).thenReturn(Optional.of(existingPe));
        when(positionService.availableQuantityToSell(existingPe)).thenReturn(260);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-001"), eq("NIFTY"), eq(260), isNull(), eq(false)))
                .thenReturn(RiskDecision.approved());

        when(fnoTradeConfigService.getEffectiveParams(USER_ID, "NIFTY"))
                .thenReturn(new FnoResolutionParams("CE", "NEAREST", "ATM", 0, 2, null)); // 2 lots
        OptionContract ce65 = OptionContract.builder().tradingSymbol("NIFTY25SEP25000CE").lotSize(65).exchange("NSE")
                .segment("FNO").optionType(OptionType.CE).strike(BigDecimal.valueOf(25000)).buyAllowed(true).sellAllowed(true).build();
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(ce65);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-001"), eq("NIFTY"), eq(130), eq(ce65), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeast(2)).save(captor.capture());
        OrderEntity closeOrder = captor.getAllValues().stream().filter(o -> o.getAction() == TradingAction.SELL).findFirst().orElseThrow();
        OrderEntity openOrder = captor.getAllValues().stream().filter(o -> o.getAction() == TradingAction.BUY).findFirst().orElseThrow();
        assertThat(closeOrder.getQuantity()).isEqualTo(260);
        assertThat(openOrder.getQuantity()).isEqualTo(130);
    }

    @Test
    void test11_existingCe130_newPeQuantity65_oldAndNewQuantitiesAreIndependent() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = sellSignal();
        when(signalRepository.findBySignalId("NIFTY-002")).thenReturn(Optional.of(signal));

        Position existingCe = openPosition("NIFTY25SEP25000CE", 130);
        when(positionService.findActivePosition(USER_ID, "NIFTY", OptionType.CE, "NIFTY-002")).thenReturn(Optional.of(existingCe));
        when(positionService.availableQuantityToSell(existingCe)).thenReturn(130);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-002"), eq("NIFTY"), eq(130), isNull(), eq(false)))
                .thenReturn(RiskDecision.approved());

        OptionContract pe65 = peContract("NIFTY25SEP25000PE", 65);
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(pe65);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-002"), eq("NIFTY"), eq(65), eq(pe65), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-002");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeast(2)).save(captor.capture());
        OrderEntity closeOrder = captor.getAllValues().stream().filter(o -> o.getAction() == TradingAction.SELL).findFirst().orElseThrow();
        OrderEntity openOrder = captor.getAllValues().stream().filter(o -> o.getAction() == TradingAction.BUY).findFirst().orElseThrow();
        assertThat(closeOrder.getQuantity()).isEqualTo(130);
        assertThat(openOrder.getQuantity()).isEqualTo(65);
    }

    @Test
    void test12_ceToPeSwitch_forcesPeOptionTypeRegardlessOfConfiguredDefault() {
        // fnoTradeConfigService is stubbed (see setUp) to always report
        // optionType="CE" - proving the BEARISH leg's PE resolution comes
        // from signal direction, not the configured/stale value.
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = sellSignal();
        when(signalRepository.findBySignalId("NIFTY-002")).thenReturn(Optional.of(signal));
        OptionContract pe = peContract("NIFTY25SEP24900PE", 75);
        when(optionContractResolver.resolve(any(), any(), any())).thenReturn(pe);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-002"), eq("NIFTY"), eq(75), eq(pe), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-002");

        ArgumentCaptor<FnoResolutionParams> paramsCaptor = ArgumentCaptor.forClass(FnoResolutionParams.class);
        verify(optionContractResolver).resolve(eq("NIFTY"), eq(signal.getPrice()), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().optionType()).isEqualTo("PE");
        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
    }

    @Test
    void test13_peToCeSwitch_resolvesCeOptionTypeForBullishSignal() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(any(), any(), any())).thenReturn(contract);
        when(riskManagementService.evaluate(eq(USER_ID), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        ArgumentCaptor<FnoResolutionParams> paramsCaptor = ArgumentCaptor.forClass(FnoResolutionParams.class);
        verify(optionContractResolver).resolve(eq("NIFTY"), eq(signal.getPrice()), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().optionType()).isEqualTo("CE");
        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
    }

    @Test
    void buySignal_liveMode_submitsRealOrderToGroww() {
        properties.setMode(TradingMode.LIVE);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(any(), any(), any(), anyInt(), any(), anyBoolean())).thenReturn(RiskDecision.approved());

        GrowwOrderResponse response = new GrowwOrderResponse();
        response.setGrowwOrderId("GID999");
        response.setOrderStatus("OPEN");
        when(growwApiClient.createOrder(eq(USER_ID), any())).thenReturn(response);

        engine.processSignal("NIFTY-001");

        verify(growwApiClient).createOrder(eq(USER_ID), argThat(req ->
                "NIFTY25SEP25000CE".equals(req.getTradingSymbol())
                        && "BUY".equals(req.getTransactionType())
                        && req.getQuantity() == 75
                        && "FNO".equals(req.getSegment())));
        verify(growwOrderStatusService).refreshByGrowwOrderId(eq(USER_ID), any());
        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
    }

    @Test
    void buySignal_liveMode_growwRejectsOrder_marksOrderRejected() {
        properties.setMode(TradingMode.LIVE);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(any(), any(), any(), anyInt(), any(), anyBoolean())).thenReturn(RiskDecision.approved());
        when(growwApiClient.createOrder(eq(USER_ID), any())).thenThrow(GrowwApiException.apiError("Invalid trading symbol", "GA001"));

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("ORDER_REJECTED");
        verify(auditService).record(eq(AuditEventType.ORDER_REJECTED), any(), any(), any());
    }

    @Test
    void distributedLock_busy_rejectsWithoutProcessing() {
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(distributedLockService.tryLock(anyString(), any())).thenReturn(Optional.empty());

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("LOCK_BUSY");
        verifyNoInteractions(optionContractResolver);
    }

    @Test
    void alreadyDuplicateSignal_isNeverProcessed() {
        TradingSignalEntity signal = buySignal();
        signal.setStatus(SignalStatus.DUPLICATE);
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));

        engine.processSignal("NIFTY-001");

        verifyNoInteractions(tradingStateService);
        verifyNoInteractions(optionContractResolver);
    }

    // ------------------------------------------------------------------
    // Automatic profit-target: the entry hands the order to the monitor.
    // ------------------------------------------------------------------

    @Test
    void paperEntry_registersTargetMonitor_withTheSignalPriceAsTheSimulatedFill() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(any(), any(), any(), anyInt(), any(), anyBoolean())).thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(positionTargetService).onEntryFilled(orderCaptor.capture(), eq(contract),
                eq(new BigDecimal("8")), eq(true));
        assertThat(orderCaptor.getValue().getAction()).isEqualTo(TradingAction.BUY);
        assertThat(orderCaptor.getValue().getAverageFillPrice()).isEqualByComparingTo(signal.getPrice());
    }

    @Test
    void contractNotFound_doesNotRegisterTargetMonitor() {
        properties.setMode(TradingMode.PAPER);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(anyString(), any(), any()))
                .thenThrow(new com.example.trading.exception.ContractNotFoundException("no such contract"));

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(signal.getRejectionReason()).contains("CONTRACT_NOT_FOUND");
        verifyNoInteractions(positionTargetService);
    }

    // ------------------------------------------------------------------
    // Multi-user fan-out: one common TradingView signal executed
    // independently for every connected Groww user - never routed to a
    // single "the" user. See TradingEngineService#executeForUser /
    // #finalizeSignal and GrowwUserResolver#findAllConnectedUserIds.
    // ------------------------------------------------------------------

    private SignalExecutionEntity executionFor(List<SignalExecutionEntity> executions, Long userId) {
        return executions.stream().filter(e -> e.getUserId().equals(userId)).findFirst()
                .orElseThrow(() -> new AssertionError("No SignalExecutionEntity recorded for userId=" + userId));
    }

    /** Test 1: three connected users (A, B, C) all execute the same BUY independently. */
    @Test
    void multiUser_threeConnectedUsers_allThreeExecuteTheSameBuySignal() {
        properties.setMode(TradingMode.PAPER);
        when(growwUserResolver.findAllConnectedUserIds()).thenReturn(List.of(USER_A, USER_B, USER_C));
        when(growwAuthenticationService.isAuthenticated(USER_B)).thenReturn(true);
        when(growwAuthenticationService.isAuthenticated(USER_C)).thenReturn(true);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(any(), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        verify(riskManagementService).evaluate(eq(USER_A), any(), any(), anyInt(), any(), eq(true));
        verify(riskManagementService).evaluate(eq(USER_B), any(), any(), anyInt(), any(), eq(true));
        verify(riskManagementService).evaluate(eq(USER_C), any(), any(), anyInt(), any(), eq(true));

        List<SignalExecutionEntity> executions = signalExecutionRepository.findBySignalId("NIFTY-001");
        assertThat(executions).hasSize(3);
        assertThat(executions).allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(SignalStatus.EXECUTED));

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeast(3)).save(orderCaptor.capture());
        assertThat(orderCaptor.getAllValues()).extracting(OrderEntity::getUserId)
                .contains(USER_A, USER_B, USER_C);
    }

    /** Test 2: A connected, B disconnected (never in GrowwUserResolver's list), C connected - only A and C are ever attempted. */
    @Test
    void multiUser_disconnectedUserIsNeverAttempted_onlyConnectedUsersExecute() {
        properties.setMode(TradingMode.PAPER);
        when(growwUserResolver.findAllConnectedUserIds()).thenReturn(List.of(USER_A, USER_C));
        when(growwAuthenticationService.isAuthenticated(USER_C)).thenReturn(true);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(any(), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.EXECUTED);
        verify(riskManagementService).evaluate(eq(USER_A), any(), any(), anyInt(), any(), eq(true));
        verify(riskManagementService).evaluate(eq(USER_C), any(), any(), anyInt(), any(), eq(true));
        verify(riskManagementService, never()).evaluate(eq(USER_B), any(), any(), anyInt(), any(), anyBoolean());
        verify(growwAuthenticationService, never()).isAuthenticated(USER_B);

        List<SignalExecutionEntity> executions = signalExecutionRepository.findBySignalId("NIFTY-001");
        assertThat(executions).extracting(SignalExecutionEntity::getUserId).containsExactlyInAnyOrder(USER_A, USER_C);
    }

    /** Test 3: A succeeds, B's order is rejected by risk, C still executes - B's failure never blocks C. */
    @Test
    void multiUser_oneUsersFailureNeverBlocksTheOthers_signalEndsPartiallyExecuted() {
        properties.setMode(TradingMode.PAPER);
        when(growwUserResolver.findAllConnectedUserIds()).thenReturn(List.of(USER_A, USER_B, USER_C));
        when(growwAuthenticationService.isAuthenticated(USER_B)).thenReturn(true);
        when(growwAuthenticationService.isAuthenticated(USER_C)).thenReturn(true);
        TradingSignalEntity signal = buySignal();
        when(signalRepository.findBySignalId("NIFTY-001")).thenReturn(Optional.of(signal));
        when(optionContractResolver.resolve(eq("NIFTY"), eq(signal.getPrice()), any())).thenReturn(contract);
        when(riskManagementService.evaluate(eq(USER_A), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.approved());
        when(riskManagementService.evaluate(eq(USER_B), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.rejected("MAX_ORDERS_PER_DAY_EXCEEDED", "too many orders"));
        when(riskManagementService.evaluate(eq(USER_C), eq("NIFTY-001"), eq("NIFTY"), eq(75), eq(contract), eq(true)))
                .thenReturn(RiskDecision.approved());

        engine.processSignal("NIFTY-001");

        assertThat(signal.getStatus()).isEqualTo(SignalStatus.PARTIALLY_EXECUTED);

        List<SignalExecutionEntity> executions = signalExecutionRepository.findBySignalId("NIFTY-001");
        assertThat(executions).hasSize(3);
        assertThat(executionFor(executions, USER_A).getStatus()).isEqualTo(SignalStatus.EXECUTED);
        assertThat(executionFor(executions, USER_B).getStatus()).isEqualTo(SignalStatus.REJECTED);
        assertThat(executionFor(executions, USER_B).getRejectionReason()).contains("MAX_ORDERS_PER_DAY_EXCEEDED");
        assertThat(executionFor(executions, USER_C).getStatus()).isEqualTo(SignalStatus.EXECUTED);

        // C must still have been fully attempted (order placed) despite B's failure.
        verify(riskManagementService).evaluate(eq(USER_C), any(), any(), anyInt(), any(), eq(true));
        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        assertThat(orderCaptor.getAllValues()).extracting(OrderEntity::getUserId).contains(USER_C);
    }

    /**
     * Test 4 (duplicate webhook delivery -> no duplicate order) is covered
     * at the intake boundary, not here: see
     * {@code TradingViewWebhookControllerTest#duplicateSignal_stillReturnsAccepted_butNeverSavesOrDispatches}.
     * The existing Redis-backed {@code SignalDeduplicationService} (atomic
     * SETNX, 24h TTL) guarantees {@link TradingEngineService#processSignal}
     * itself is only ever invoked once for a given signalId; since the
     * per-user fan-out loop inside that single invocation runs exactly
     * once per connected user, "TradingView alert + user = one execution"
     * falls out of that existing guarantee with no new dedup mechanism
     * needed.
     */

    /** Test 5 (zero connected users -> handled gracefully, no order attempted) is {@link #buySignal_noConnectedGrowwUser_rejectsWithoutTouchingAuthOrRisk}. */
}
