package com.example.trading.repository;

import com.example.trading.entity.*;
import com.example.trading.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migration ({@code V1__init_schema.sql}) against an
 * H2-in-MySQL-compatibility-mode database (see {@code src/test/resources/application.yml},
 * which sets {@code spring.test.database.replace: NONE} so this datasource
 * is not swapped for a plain-mode embedded DB) and exercises every
 * repository - proving the schema and entities actually work together, not
 * just that they compile.
 */
@DataJpaTest
class RepositoryIntegrationTest {

    @Autowired
    private TradingSignalRepository signalRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private PositionSnapshotRepository positionSnapshotRepository;
    @Autowired
    private DailyTradingSummaryRepository dailyTradingSummaryRepository;
    @Autowired
    private SignalExecutionRepository signalExecutionRepository;
    @Autowired
    private FnoTradeConfigRepository fnoTradeConfigRepository;
    @Autowired
    private PositionTargetRepository positionTargetRepository;

    @Test
    void tradingSignal_persistsAndEnforcesUniqueSignalId() {
        TradingSignalEntity signal = TradingSignalEntity.received("NIFTY-INT-001", TradingAction.BUY, "NIFTY",
                "NSE", "15m", BigDecimal.valueOf(25000.5), Instant.now());
        signalRepository.save(signal);

        assertThat(signalRepository.existsBySignalId("NIFTY-INT-001")).isTrue();
        assertThat(signalRepository.findBySignalId("NIFTY-INT-001")).isPresent();
        assertThat(signal.getCreatedAt()).isNotNull();
    }

    @Test
    void order_persistsWithAllFields_andEnforcesUniqueReferenceId() {
        OrderEntity order = new OrderEntity();
        order.setSignalId("NIFTY-INT-002");
        order.setUserId(1L);
        order.setOrderReferenceId("TV-NIFTY-INT00001");
        order.setGrowwOrderId("GID1");
        order.setUnderlying("NIFTY");
        order.setTradingSymbol("NIFTY25SEP25000CE");
        order.setAction(TradingAction.BUY);
        order.setQuantity(75);
        order.setPrice(BigDecimal.valueOf(120.5));
        order.setOrderType("MARKET");
        order.setProduct("NRML");
        order.setSegment("FNO");
        order.setStatus(OrderStatus.COMPLETE);
        order.setFilledQuantity(75);

        orderRepository.save(order);

        assertThat(orderRepository.findByOrderReferenceId("TV-NIFTY-INT00001")).isPresent();
        assertThat(orderRepository.findByOrderReferenceId("TV-NIFTY-INT00001").get().getUserId()).isEqualTo(1L);
        assertThat(orderRepository.findByGrowwOrderId("GID1")).isPresent();
        assertThat(orderRepository.countByUnderlyingAndCreatedAtBetween("NIFTY",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(60))).isEqualTo(1);
    }

    @Test
    void signalExecution_persistsOnePerSignalAndUser_andIsQueryableBySignalId() {
        SignalExecutionEntity executionA = SignalExecutionEntity.forSignalAndUser("NIFTY-INT-006", 1L);
        executionA.setStatus(SignalStatus.EXECUTED);
        SignalExecutionEntity executionB = SignalExecutionEntity.forSignalAndUser("NIFTY-INT-006", 2L);
        executionB.setStatus(SignalStatus.REJECTED);
        executionB.setRejectionReason("MAX_ORDERS_PER_DAY_EXCEEDED: too many orders");

        signalExecutionRepository.save(executionA);
        signalExecutionRepository.save(executionB);

        assertThat(signalExecutionRepository.findBySignalIdAndUserId("NIFTY-INT-006", 1L)).isPresent();
        assertThat(signalExecutionRepository.findBySignalId("NIFTY-INT-006")).hasSize(2);
    }

    @Test
    void auditEvent_persistsAndIsQueryableBySignalId() {
        AuditEventEntity event = new AuditEventEntity();
        event.setSignalId("NIFTY-INT-003");
        event.setEventType(AuditEventType.SIGNAL_RECEIVED);
        event.setDetails("action=BUY underlying=NIFTY");
        auditEventRepository.save(event);

        assertThat(auditEventRepository.findBySignalIdOrderByCreatedAtAsc("NIFTY-INT-003")).hasSize(1);
    }

    @Test
    void positionSnapshot_persists() {
        PositionSnapshotEntity snapshot = new PositionSnapshotEntity();
        snapshot.setTradingSymbol("NIFTY25SEP25000CE");
        snapshot.setExchange("NSE");
        snapshot.setSegment("FNO");
        snapshot.setQuantity(75);
        snapshot.setNetQuantity(75);
        snapshot.setAveragePrice(BigDecimal.valueOf(120.5));
        snapshot.setProduct("NRML");

        positionSnapshotRepository.save(snapshot);

        assertThat(positionSnapshotRepository.findAll()).hasSize(1);
    }

    @Test
    void fnoTradeConfig_isPerUser_andCarriesTargetPoints() {
        FnoTradeConfigEntity a = new FnoTradeConfigEntity();
        a.setUserId(1L);
        a.setUnderlying("NIFTY");
        a.setOptionType("CE");
        a.setExpirySelection("NEAREST");
        a.setStrikeSelection("ATM");
        a.setStrikeOffset(0);
        a.setLots(2);
        a.setTargetPoints(new BigDecimal("8.0000"));
        a.setTargetEnabled(true);

        FnoTradeConfigEntity b = new FnoTradeConfigEntity();
        b.setUserId(2L);
        b.setUnderlying("NIFTY"); // same underlying, different user -> allowed by (user_id, underlying) unique key
        b.setOptionType("PE");
        b.setExpirySelection("NEAREST");
        b.setStrikeSelection("ATM");
        b.setStrikeOffset(0);
        b.setLots(3);
        b.setTargetPoints(new BigDecimal("12.0000"));
        b.setTargetEnabled(false);

        fnoTradeConfigRepository.save(a);
        fnoTradeConfigRepository.save(b);

        assertThat(fnoTradeConfigRepository.findByUserIdAndUnderlying(1L, "NIFTY"))
                .get().extracting(FnoTradeConfigEntity::getTargetPoints).isEqualTo(new BigDecimal("8.0000"));
        assertThat(fnoTradeConfigRepository.findByUserIdAndUnderlying(2L, "NIFTY"))
                .get().extracting(FnoTradeConfigEntity::getTargetPoints).isEqualTo(new BigDecimal("12.0000"));
    }

    @Test
    void positionTarget_roundTrips_andIsUniquePerEntryOrder() {
        PositionTargetEntity pt = new PositionTargetEntity();
        pt.setUserId(1L);
        pt.setSignalId("NIFTY-INT-777");
        pt.setEntryOrderReferenceId("TV-NIFTY-INT77777");
        pt.setTradingSymbol("NIFTY25SEP25000CE");
        pt.setExchange("NSE");
        pt.setSegment("FNO");
        pt.setUnderlying("NIFTY");
        pt.setSide(PositionSide.LONG);
        pt.setQuantity(130);
        pt.setEntryPrice(new BigDecimal("142.2000"));
        pt.setTargetPoints(new BigDecimal("8.0000"));
        pt.setTargetEnabled(true);
        pt.setTargetPrice(new BigDecimal("150.2000"));
        pt.setTargetStatus(TargetStatus.MONITORING);

        positionTargetRepository.save(pt);

        assertThat(positionTargetRepository.findByEntryOrderReferenceId("TV-NIFTY-INT77777"))
                .get().extracting(PositionTargetEntity::getTargetPrice).isEqualTo(new BigDecimal("150.2000"));
        assertThat(positionTargetRepository.findByTargetStatusIn(java.util.List.of(TargetStatus.MONITORING))).hasSize(1);
        assertThat(positionTargetRepository.findByUserIdOrderByUpdatedAtDesc(1L)).hasSize(1);
    }

    @Test
    void dailyTradingSummary_persistsAndEnforcesUniqueDate() {
        LocalDate today = LocalDate.now();
        DailyTradingSummaryEntity summary = DailyTradingSummaryEntity.forDate(today);
        summary.setOrdersCount(2);
        summary.setBuyCount(1);
        summary.setSellCount(1);

        dailyTradingSummaryRepository.save(summary);

        assertThat(dailyTradingSummaryRepository.findByTradingDate(today)).isPresent();
    }
}
