package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.OptionContract;
import com.example.trading.dto.Position;
import com.example.trading.entity.OrderEntity;
import com.example.trading.entity.PositionTargetEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OptionType;
import com.example.trading.enums.OrderStatus;
import com.example.trading.enums.TargetStatus;
import com.example.trading.enums.TradingAction;
import com.example.trading.enums.TradingMode;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.redis.DistributedLockService;
import com.example.trading.repository.OrderRepository;
import com.example.trading.repository.PositionTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The automatic profit-target monitor: target = ACTUAL fill + points,
 * exactly one SELL when LTP >= target, correct exit quantity, PAPER vs LIVE,
 * LTP de-duplication, and restart recovery.
 */
class PositionTargetServiceTest {

    private static final Long USER = 7L;
    private static final String SYMBOL = "NIFTY25SEP25000CE";
    private static final String LTP_KEY = "NSE_" + SYMBOL;

    private PositionTargetRepository repository;
    private TradingProperties properties;
    private GrowwApiClient growwApiClient;
    private GrowwAuthenticationService growwAuthenticationService;
    private GrowwPositionService growwPositionService;
    private GrowwOrderStatusService growwOrderStatusService;
    private OrderExecutionSupport orderExecutionSupport;
    private OrderRepository orderRepository;
    private DistributedLockService distributedLockService;
    private AuditService auditService;
    private PositionTargetService service;

    private Map<Long, PositionTargetEntity> store;
    private List<OrderEntity> savedOrders;

    @BeforeEach
    void setUp() {
        repository = mock(PositionTargetRepository.class);
        properties = new TradingProperties();
        properties.setMode(TradingMode.PAPER);
        growwApiClient = mock(GrowwApiClient.class);
        growwAuthenticationService = mock(GrowwAuthenticationService.class);
        growwPositionService = mock(GrowwPositionService.class);
        growwOrderStatusService = mock(GrowwOrderStatusService.class);
        orderExecutionSupport = mock(OrderExecutionSupport.class);
        orderRepository = mock(OrderRepository.class);
        distributedLockService = mock(DistributedLockService.class);
        auditService = mock(AuditService.class);

        service = new PositionTargetService(repository, new TargetCalculator(), properties, growwApiClient,
                growwAuthenticationService, growwPositionService, growwOrderStatusService, orderExecutionSupport,
                orderRepository, new OrderReferenceGenerator(), distributedLockService, auditService);

        // in-memory position_target
        store = new HashMap<>();
        AtomicLong seq = new AtomicLong();
        when(repository.save(any(PositionTargetEntity.class))).thenAnswer(inv -> {
            PositionTargetEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(seq.incrementAndGet());
            e.setVersion(e.getVersion() == null ? 0L : e.getVersion() + 1);
            store.put(e.getId(), e);
            return e;
        });
        when(repository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(repository.findByEntryOrderReferenceId(anyString())).thenAnswer(inv -> store.values().stream()
                .filter(e -> e.getEntryOrderReferenceId().equals(inv.getArgument(0))).findFirst());
        when(repository.findByTargetStatusIn(anyList())).thenAnswer(inv -> {
            List<TargetStatus> wanted = inv.getArgument(0);
            return store.values().stream().filter(e -> wanted.contains(e.getTargetStatus())).collect(Collectors.toList());
        });
        doAnswer(inv -> {
            PositionTargetEntity e = store.get(inv.getArgument(0));
            if (e != null && e.getTargetStatus() == TargetStatus.MONITORING) {
                e.setLastLtp(inv.getArgument(1));
                e.setLastLtpAt(inv.getArgument(2));
            }
            return null;
        }).when(repository).updateLastLtp(anyLong(), any(), any());

        savedOrders = new ArrayList<>();
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            savedOrders.add(o);
            return o;
        });
        when(orderRepository.findByOrderReferenceId(anyString())).thenReturn(Optional.empty());

        when(distributedLockService.tryLock(anyString(), any())).thenReturn(Optional.of("lock"));
        when(growwAuthenticationService.isAuthenticated(anyLong())).thenReturn(true);
    }

    private OptionContract contract() {
        return OptionContract.builder().tradingSymbol(SYMBOL).underlying("NIFTY").exchange("NSE").segment("FNO")
                .optionType(OptionType.CE).lotSize(65).build();
    }

    private OrderEntity entryOrder(String ref, BigDecimal fillPrice, int filledQty) {
        OrderEntity o = new OrderEntity();
        o.setUserId(USER);
        o.setSignalId("SIG-" + ref);
        o.setOrderReferenceId(ref);
        o.setGrowwOrderId("GID-" + ref);
        o.setUnderlying("NIFTY");
        o.setTradingSymbol(SYMBOL);
        o.setAction(TradingAction.BUY);
        o.setQuantity(130);
        o.setFilledQuantity(filledQty);
        o.setAverageFillPrice(fillPrice);
        o.setPrice(new BigDecimal("141.50")); // the TradingView signal price - must be IGNORED
        o.setStatus(OrderStatus.COMPLETE);
        return o;
    }

    private void stubLtp(String value) {
        when(growwApiClient.getLtps(eq(USER), eq("NSE"), eq("FNO"), anyList()))
                .thenReturn(value == null ? Map.of() : Map.of(LTP_KEY, new BigDecimal(value)));
    }

    private List<OrderEntity> sellOrders() {
        // the fake orderRepository.save records every call; simulate/reconcile can re-save the
        // SAME entity, so dedupe by identity to count DISTINCT exit orders.
        return savedOrders.stream()
                .filter(o -> o.getAction() == TradingAction.SELL)
                .distinct()
                .collect(Collectors.toList());
    }

    private PositionTargetEntity theRow() {
        return store.values().iterator().next();
    }

    // ------------------------------------------------------------------

    @Test
    void onEntryFilled_withActualFill_startsMonitoring_targetFromFillNotSignalPrice() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142.00"), 130), contract(), new BigDecimal("8"), true);

        PositionTargetEntity pt = theRow();
        assertThat(pt.getTargetStatus()).isEqualTo(TargetStatus.MONITORING);
        assertThat(pt.getEntryPrice()).isEqualByComparingTo("142.00");
        assertThat(pt.getTargetPrice()).isEqualByComparingTo("150.00"); // 142 + 8, NOT 141.50 + 8
        assertThat(pt.getQuantity()).isEqualTo(130);
        verify(auditService).record(eq(AuditEventType.TARGET_MONITOR_STARTED), any(), any(), any());
    }

    @Test
    void onEntryFilled_decimalFill_142_25_points8_target150_25() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142.25"), 130), contract(), new BigDecimal("8"), true);
        assertThat(theRow().getTargetPrice()).isEqualByComparingTo("150.25");
    }

    @Test
    void onEntryFilled_noFillPrice_staysPendingEntry_andIsNotPolled() {
        service.onEntryFilled(entryOrder("E1", null, 0), contract(), new BigDecimal("8"), true);

        assertThat(theRow().getTargetStatus()).isEqualTo(TargetStatus.PENDING_ENTRY);
        assertThat(theRow().getTargetPrice()).isNull();
        verify(auditService).record(eq(AuditEventType.TARGET_MONITOR_DEFERRED), any(), any(), any());

        service.runMonitorTick();
        verify(growwApiClient, never()).getLtps(anyLong(), anyString(), anyString(), anyList());
    }

    @Test
    void onEntryFilled_isIdempotent_perEntryOrder() {
        OrderEntity order = entryOrder("E1", new BigDecimal("142"), 130);
        service.onEntryFilled(order, contract(), new BigDecimal("8"), true);
        service.onEntryFilled(order, contract(), new BigDecimal("8"), true);
        assertThat(store).hasSize(1);
    }

    @Test
    void onEntryFilled_targetDisabled_createsNoRow() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 130), contract(), new BigDecimal("8"), false);
        assertThat(store).isEmpty();
    }

    @Test
    void ltpBelowTarget_noExit() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 130), contract(), new BigDecimal("8"), true);
        stubLtp("149.99");

        service.runMonitorTick();

        assertThat(theRow().getTargetStatus()).isEqualTo(TargetStatus.MONITORING);
        assertThat(sellOrders()).isEmpty();
        assertThat(theRow().getLastLtp()).isEqualByComparingTo("149.99");
    }

    @Test
    void ltpExactlyAtTarget_exitsInPaperMode_withoutCallingGroww() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 130), contract(), new BigDecimal("8"), true);
        stubLtp("150.00");

        service.runMonitorTick();

        PositionTargetEntity pt = theRow();
        assertThat(pt.getTargetStatus()).isEqualTo(TargetStatus.CLOSED);
        assertThat(pt.getExitedQuantity()).isEqualTo(130);
        assertThat(pt.getLtpAtTrigger()).isEqualByComparingTo("150.00");
        assertThat(sellOrders()).hasSize(1);
        assertThat(sellOrders().get(0).getQuantity()).isEqualTo(130);
        verifyNoInteractions(orderExecutionSupport);
        verify(auditService).record(eq(AuditEventType.TARGET_HIT), any(), any(), any());
        verify(auditService).record(eq(AuditEventType.TARGET_POSITION_CLOSED), any(), any(), any());
    }

    @Test
    void ltpAboveTarget_exits() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 130), contract(), new BigDecimal("8"), true);
        stubLtp("150.50");

        service.runMonitorTick();

        assertThat(theRow().getTargetStatus()).isEqualTo(TargetStatus.CLOSED);
        assertThat(sellOrders()).hasSize(1);
    }

    @Test
    void rapidLtpUpdates_produceExactlyOneExitOrder() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 130), contract(), new BigDecimal("8"), true);
        PositionTargetEntity monitoring = theRow();

        service.checkAndExit(monitoring, new BigDecimal("150.01"));
        service.checkAndExit(monitoring, new BigDecimal("150.10"));
        service.checkAndExit(monitoring, new BigDecimal("150.25"));

        assertThat(sellOrders()).hasSize(1);
        assertThat(theRow().getTargetStatus()).isEqualTo(TargetStatus.CLOSED);
    }

    @Test
    void liveMode_exitsForTheActualRemainingBrokerQuantity_notTheStoredQuantity() {
        properties.setMode(TradingMode.LIVE);
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 130), contract(), new BigDecimal("8"), true);
        // original 130, but only 65 remain at the broker (a partial prior exit)
        when(growwPositionService.getPositions(USER)).thenReturn(List.of(
                Position.builder().tradingSymbol(SYMBOL).exchange("NSE").netQuantity(65).build()));
        when(orderExecutionSupport.submitAndReconcile(eq(USER), any(OrderEntity.class), any())).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(1);
            o.setStatus(OrderStatus.COMPLETE);
            o.setFilledQuantity(o.getQuantity());
            return o;
        });
        stubLtp("150.00");

        service.runMonitorTick();

        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderExecutionSupport).submitAndReconcile(eq(USER), captor.capture(), any());
        assertThat(captor.getValue().getAction()).isEqualTo(TradingAction.SELL);
        assertThat(captor.getValue().getQuantity()).isEqualTo(65);
        assertThat(theRow().getTargetStatus()).isEqualTo(TargetStatus.CLOSED);
        assertThat(theRow().getExitedQuantity()).isEqualTo(65);
    }

    @Test
    void liveMode_brokerAlreadyFlat_marksClosedWithoutSubmittingASell() {
        properties.setMode(TradingMode.LIVE);
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 130), contract(), new BigDecimal("8"), true);
        when(growwPositionService.getPositions(USER)).thenReturn(List.of()); // e.g. already closed by the 15:10 safety exit
        stubLtp("150.00");

        service.runMonitorTick();

        assertThat(theRow().getTargetStatus()).isEqualTo(TargetStatus.CLOSED);
        assertThat(sellOrders()).isEmpty();
        verifyNoInteractions(orderExecutionSupport);
        verify(auditService).record(eq(AuditEventType.TARGET_POSITION_CLOSED), any(), any(), any());
    }

    @Test
    void nullLtp_neverAssumesTargetReached() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 130), contract(), new BigDecimal("8"), true);
        stubLtp(null); // Groww returned nothing this tick

        service.runMonitorTick();

        assertThat(theRow().getTargetStatus()).isEqualTo(TargetStatus.MONITORING);
        assertThat(sellOrders()).isEmpty();
        verify(auditService).record(eq(AuditEventType.TARGET_LTP_UNAVAILABLE), any(), any(), any());
    }

    @Test
    void ltpRequestsAreDeDuplicatedByContract() {
        service.onEntryFilled(entryOrder("E1", new BigDecimal("142"), 65), contract(), new BigDecimal("8"), true);
        service.onEntryFilled(entryOrder("E2", new BigDecimal("142"), 65), contract(), new BigDecimal("8"), true);
        assertThat(store).hasSize(2);
        stubLtp("149.00");

        service.runMonitorTick();

        // two monitored positions on the SAME contract -> ONE batched LTP call
        verify(growwApiClient, times(1)).getLtps(eq(USER), eq("NSE"), eq("FNO"), anyList());
    }

    @Test
    void restartRecovery_reconcilesAnInFlightExit_andResumesMonitoring() {
        // a CLOSING row whose exit order actually completed while the JVM was down
        PositionTargetEntity closing = new PositionTargetEntity();
        closing.setId(1L);
        closing.setUserId(USER);
        closing.setSignalId("SIG");
        closing.setEntryOrderReferenceId("E1");
        closing.setTradingSymbol(SYMBOL);
        closing.setExchange("NSE");
        closing.setSegment("FNO");
        closing.setUnderlying("NIFTY");
        closing.setQuantity(130);
        closing.setTargetPoints(new BigDecimal("8"));
        closing.setTargetPrice(new BigDecimal("150.00"));
        closing.setTargetStatus(TargetStatus.CLOSING);
        closing.setExitOrderReferenceId("TGTEXIT-1");
        store.put(1L, closing);

        OrderEntity exitOrder = new OrderEntity();
        exitOrder.setOrderReferenceId("TGTEXIT-1");
        exitOrder.setAction(TradingAction.SELL);
        exitOrder.setQuantity(130);
        exitOrder.setFilledQuantity(130);
        exitOrder.setStatus(OrderStatus.COMPLETE);
        when(orderRepository.findByOrderReferenceId("TGTEXIT-1")).thenReturn(Optional.of(exitOrder));

        service.recoverOnStartup();

        assertThat(store.get(1L).getTargetStatus()).isEqualTo(TargetStatus.CLOSED);
        verify(auditService).record(eq(AuditEventType.TARGET_MONITOR_RESUMED), any(), any(), any());
    }

    @Test
    void restartRecovery_keepsUsingThePersistedTargetPrice() {
        PositionTargetEntity monitoring = new PositionTargetEntity();
        monitoring.setId(1L);
        monitoring.setUserId(USER);
        monitoring.setSignalId("SIG");
        monitoring.setEntryOrderReferenceId("E1");
        monitoring.setTradingSymbol(SYMBOL);
        monitoring.setExchange("NSE");
        monitoring.setSegment("FNO");
        monitoring.setUnderlying("NIFTY");
        monitoring.setQuantity(130);
        monitoring.setEntryPrice(new BigDecimal("142.00"));
        monitoring.setTargetPoints(new BigDecimal("8"));
        monitoring.setTargetPrice(new BigDecimal("150.00")); // persisted before the restart
        monitoring.setTargetStatus(TargetStatus.MONITORING);
        store.put(1L, monitoring);

        service.recoverOnStartup();
        stubLtp("150.00");
        service.runMonitorTick();

        assertThat(store.get(1L).getTargetStatus()).isEqualTo(TargetStatus.CLOSED);
        assertThat(store.get(1L).getLtpAtTrigger()).isEqualByComparingTo("150.00");
    }
}
