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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The 15:10 SAFETY EXIT: closes only SYSTEM-MANAGED positions, using the
 * ACTUAL broker quantity (never configured lots), de-duplicated by a
 * deterministic reference and guarded by a per-day Redis lock.
 */
class SessionCloseServiceTest {

    private static final Long USER = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);
    private static final String MANAGED_SYMBOL = "NIFTY25SEP25000CE";

    private TradingProperties properties;
    private TradingSessionService tradingSessionService;
    private GrowwUserResolver growwUserResolver;
    private GrowwAuthenticationService growwAuthenticationService;
    private GrowwPositionService growwPositionService;
    private GrowwOrderStatusService growwOrderStatusService;
    private OrderExecutionSupport orderExecutionSupport;
    private OrderRepository orderRepository;
    private DistributedLockService distributedLockService;
    private AuditService auditService;
    private final OrderReferenceGenerator orderReferenceGenerator = new OrderReferenceGenerator();

    private SessionCloseService service;

    @BeforeEach
    void setUp() {
        properties = new TradingProperties();
        properties.setMode(TradingMode.PAPER);
        tradingSessionService = mock(TradingSessionService.class);
        growwUserResolver = mock(GrowwUserResolver.class);
        growwAuthenticationService = mock(GrowwAuthenticationService.class);
        growwPositionService = mock(GrowwPositionService.class);
        growwOrderStatusService = mock(GrowwOrderStatusService.class);
        orderExecutionSupport = mock(OrderExecutionSupport.class);
        orderRepository = mock(OrderRepository.class);
        distributedLockService = mock(DistributedLockService.class);
        auditService = mock(AuditService.class);

        service = new SessionCloseService(properties, tradingSessionService, growwUserResolver,
                growwAuthenticationService, growwPositionService, growwOrderStatusService, orderExecutionSupport,
                orderReferenceGenerator, orderRepository, distributedLockService, auditService);

        when(tradingSessionService.todayIst()).thenReturn(DATE);
        when(tradingSessionService.getLifecycleState()).thenReturn(SessionState.SESSION_CLOSING);
        when(distributedLockService.tryLock(anyString(), any())).thenReturn(Optional.of("lock-token"));
        when(growwUserResolver.findAllConnectedUserIds()).thenReturn(List.of(USER));
        when(growwAuthenticationService.isAuthenticated(USER)).thenReturn(true);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findByOrderReferenceId(anyString())).thenReturn(Optional.empty());
    }

    private OrderEntity managedOrder(String symbol) {
        OrderEntity o = new OrderEntity();
        o.setUserId(USER);
        o.setTradingSymbol(symbol);
        o.setAction(TradingAction.BUY);
        o.setStatus(OrderStatus.COMPLETE);
        return o;
    }

    private Position brokerPosition(String symbol, int net) {
        return Position.builder().tradingSymbol(symbol).exchange("NSE").product("NRML").netQuantity(net).build();
    }

    private void managed(String... symbols) {
        when(orderRepository.findByUserIdAndStatusAndCreatedAtBetween(eq(USER), eq(OrderStatus.COMPLETE), any(Instant.class), any(Instant.class)))
                .thenReturn(java.util.Arrays.stream(symbols).map(this::managedOrder).toList());
    }

    private List<OrderEntity> savedOrders() {
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------------

    @Test
    void longPosition_paperMode_closesActualBrokerQuantity_withSell() {
        managed(MANAGED_SYMBOL);
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(brokerPosition(MANAGED_SYMBOL, 65)));

        service.startSafetyPositionClose();

        OrderEntity close = savedOrders().stream().filter(o -> o.getAction() == TradingAction.SELL).findFirst().orElseThrow();
        assertThat(close.getTradingSymbol()).isEqualTo(MANAGED_SYMBOL);
        assertThat(close.getQuantity()).isEqualTo(65);
        assertThat(close.getOrderReferenceId()).startsWith("SESSIONCLOSE-20260910-");
        assertThat(close.getStatus()).isEqualTo(OrderStatus.COMPLETE); // PAPER simulated fill
        verifyNoInteractions(orderExecutionSupport);
        verify(auditService).record(eq(AuditEventType.AUTO_CLOSE_POSITION_STARTED), any(), any(), any());
        verify(auditService).record(eq(AuditEventType.AUTO_CLOSE_ORDER_FILLED), any(), any(), any());
        verify(tradingSessionService).markSafetyExitComplete();
    }

    @Test
    void closeQuantityIsBrokerNetQuantity_notConfiguredLots() {
        properties.getQuantity().setLots(10); // would be 10 * lotSize if (wrongly) used
        managed(MANAGED_SYMBOL);
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(brokerPosition(MANAGED_SYMBOL, 65)));

        service.startSafetyPositionClose();

        OrderEntity close = savedOrders().stream().filter(o -> o.getAction() == TradingAction.SELL).findFirst().orElseThrow();
        assertThat(close.getQuantity()).isEqualTo(65); // exactly the broker quantity
    }

    @Test
    void shortPosition_shortSellingEnabled_closesWithBuy() {
        properties.setAllowShortSelling(true);
        managed(MANAGED_SYMBOL);
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(brokerPosition(MANAGED_SYMBOL, -130)));

        service.startSafetyPositionClose();

        OrderEntity close = savedOrders().stream().filter(o -> o.getAction() == TradingAction.BUY).findFirst().orElseThrow();
        assertThat(close.getQuantity()).isEqualTo(130);
    }

    @Test
    void shortPosition_shortSellingDisabled_isSkipped_noCloseOrder() {
        properties.setAllowShortSelling(false);
        managed(MANAGED_SYMBOL);
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(brokerPosition(MANAGED_SYMBOL, -130)));

        service.startSafetyPositionClose();

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(auditService, never()).record(eq(AuditEventType.AUTO_CLOSE_POSITION_STARTED), any(), any(), any());
    }

    @Test
    void manuallyOpenedPosition_notInTodaysOrders_isNeverClosed() {
        managed(MANAGED_SYMBOL); // engine only ever traded the NIFTY CE today
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(
                brokerPosition("BANKNIFTY25SEP52000PE", 30),   // user opened this by hand in the Groww app
                brokerPosition("RELIANCE25SEP3000CE", 250)));   // and this

        service.startSafetyPositionClose();

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(tradingSessionService).markSafetyExitComplete();
    }

    @Test
    void noSystemManagedPositions_noCloseOrder_noBrokerQuery() {
        managed(); // nothing traded today
        service.startSafetyPositionClose();

        verify(growwPositionService, never()).getPositions(any());
        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(tradingSessionService).markSafetyExitComplete();
    }

    @Test
    void duplicateRun_reconcilesExistingCloseOrder_insteadOfResubmitting() {
        managed(MANAGED_SYMBOL);
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(brokerPosition(MANAGED_SYMBOL, 65)));
        String ref = orderReferenceGenerator.sessionClose(DATE, USER, MANAGED_SYMBOL);
        OrderEntity prior = new OrderEntity();
        prior.setOrderReferenceId(ref);
        prior.setStatus(OrderStatus.COMPLETE);
        when(orderRepository.findByOrderReferenceId(ref)).thenReturn(Optional.of(prior));

        service.startSafetyPositionClose();

        verify(orderRepository, never()).save(any(OrderEntity.class)); // no second close order
        verifyNoInteractions(orderExecutionSupport);
        verify(tradingSessionService).markSafetyExitComplete();
    }

    @Test
    void redisLockHeldByAnotherInstance_doesNothing() {
        when(distributedLockService.tryLock(anyString(), any())).thenReturn(Optional.empty());
        managed(MANAGED_SYMBOL);
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(brokerPosition(MANAGED_SYMBOL, 65)));

        service.startSafetyPositionClose();

        verify(tradingSessionService, never()).beginSessionClosing();
        verifyNoInteractions(growwPositionService);
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void liveMode_submitsCloseViaOrderExecutionSupport_withBrokerQuantity() {
        properties.setMode(TradingMode.LIVE);
        managed(MANAGED_SYMBOL);
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(brokerPosition(MANAGED_SYMBOL, 130)));
        when(orderExecutionSupport.submitAndReconcile(eq(USER), any(OrderEntity.class), any())).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(1);
            o.setStatus(OrderStatus.COMPLETE);
            o.setFilledQuantity(o.getQuantity());
            return o;
        });

        service.startSafetyPositionClose();

        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderExecutionSupport).submitAndReconcile(eq(USER), captor.capture(), any());
        assertThat(captor.getValue().getAction()).isEqualTo(TradingAction.SELL);
        assertThat(captor.getValue().getQuantity()).isEqualTo(130);
        verify(auditService).record(eq(AuditEventType.AUTO_CLOSE_ORDER_SUBMITTED), any(), any(), any());
        verify(auditService).record(eq(AuditEventType.AUTO_CLOSE_ORDER_FILLED), any(), any(), any());
        verify(tradingSessionService).markSafetyExitComplete();
    }

    @Test
    void autoCloseDisabled_marksClosing_butPlacesNoOrders() {
        properties.getSession().setAutoClosePositions(false);

        service.startSafetyPositionClose();

        verify(tradingSessionService).beginSessionClosing();
        verifyNoInteractions(distributedLockService);
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void monitorSafetyExit_noOpWhenNotSessionClosing() {
        when(tradingSessionService.getLifecycleState()).thenReturn(SessionState.MARKET_CLOSED);
        service.monitorSafetyExit();
        verify(distributedLockService, never()).tryLock(anyString(), any());
    }

    @Test
    void closeIsPerUser_authFailureForOneUserDoesNotBlockOthers() {
        Long userB = 2L;
        when(growwUserResolver.findAllConnectedUserIds()).thenReturn(List.of(USER, userB));
        when(growwAuthenticationService.isAuthenticated(USER)).thenReturn(false);
        when(growwAuthenticationService.authenticate(USER)).thenReturn(false);
        when(growwAuthenticationService.isAuthenticated(userB)).thenReturn(true);
        when(orderRepository.findByUserIdAndStatusAndCreatedAtBetween(eq(userB), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(managedOrder(MANAGED_SYMBOL)));
        when(orderRepository.findByUserIdAndStatusAndCreatedAtBetween(eq(USER), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(managedOrder(MANAGED_SYMBOL)));
        when(growwPositionService.getPositions(userB)).thenReturn(List.of(brokerPosition(MANAGED_SYMBOL, 50)));

        service.startSafetyPositionClose();

        // user B's position was still closed despite user A's auth failure.
        OrderEntity close = savedOrders().stream().filter(o -> o.getAction() == TradingAction.SELL).findFirst().orElseThrow();
        assertThat(close.getUserId()).isEqualTo(userB);
        assertThat(close.getQuantity()).isEqualTo(50);
        // ...and because user A could not be cleared, the session stays SESSION_CLOSING.
        verify(tradingSessionService, never()).markSafetyExitComplete();
        verify(auditService, atLeastOnce()).record(eq(AuditEventType.AUTO_CLOSE_ORDER_FAILED), any(), any(), any());
    }
}
