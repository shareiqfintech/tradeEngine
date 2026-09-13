package com.example.trading.service;

import com.example.trading.entity.OrderEntity;
import com.example.trading.enums.OrderStatus;
import com.example.trading.enums.TradingAction;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.dto.GrowwOrderResponse;
import com.example.trading.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrowwOrderStatusServiceTest {

    private static final Long USER_ID = 1L;

    private GrowwApiClient growwApiClient;
    private OrderRepository orderRepository;
    private AuditService auditService;
    private GrowwOrderStatusService service;

    @BeforeEach
    void setUp() {
        growwApiClient = mock(GrowwApiClient.class);
        orderRepository = mock(OrderRepository.class);
        auditService = mock(AuditService.class);
        service = new GrowwOrderStatusService(growwApiClient, orderRepository, auditService);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OrderEntity order(OrderStatus status) {
        OrderEntity order = new OrderEntity();
        order.setSignalId("SIG-1");
        order.setOrderReferenceId("TV-NIFTY-ABC12345");
        order.setGrowwOrderId("GID123");
        order.setTradingSymbol("NIFTY25SEP25000CE");
        order.setAction(TradingAction.BUY);
        order.setQuantity(75);
        order.setFilledQuantity(0);
        order.setStatus(status);
        return order;
    }

    @Test
    void mapStatus_mapsKnownGrowwStatuses() {
        assertThat(service.mapStatus("OPEN")).isEqualTo(OrderStatus.OPEN);
        assertThat(service.mapStatus("complete")).isEqualTo(OrderStatus.COMPLETE);
        assertThat(service.mapStatus("REJECTED")).isEqualTo(OrderStatus.REJECTED);
        assertThat(service.mapStatus("CANCELLED")).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void mapStatus_unknownValue_mapsToFailed() {
        assertThat(service.mapStatus("SOME_NEW_STATUS")).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void mapStatus_nullValue_mapsToPending() {
        assertThat(service.mapStatus(null)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void refreshByGrowwOrderId_updatesOrderFromBrokerResponse() {
        OrderEntity order = order(OrderStatus.OPEN);
        GrowwOrderResponse response = new GrowwOrderResponse();
        response.setGrowwOrderId("GID123");
        response.setOrderStatus("COMPLETE");
        response.setFilledQuantity(75);
        response.setAverageFillPrice(BigDecimal.valueOf(120.5));
        when(growwApiClient.getOrderStatus(USER_ID, "GID123", "FNO")).thenReturn(response);

        OrderEntity updated = service.refreshByGrowwOrderId(USER_ID, order);

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETE);
        assertThat(updated.getFilledQuantity()).isEqualTo(75);
        assertThat(updated.getAverageFillPrice()).isEqualTo(BigDecimal.valueOf(120.5));
        verify(auditService).record(eq(com.example.trading.enums.AuditEventType.ORDER_COMPLETED), any(), any(), any());
    }

    @Test
    void refreshByGrowwOrderId_withoutGrowwOrderId_isNoOp() {
        OrderEntity order = order(OrderStatus.PENDING);
        order.setGrowwOrderId(null);

        OrderEntity result = service.refreshByGrowwOrderId(USER_ID, order);

        assertThat(result).isSameAs(order);
        verifyNoInteractions(growwApiClient);
    }

    @Test
    void applyStatus_noStatusChange_doesNotEmitAuditEvent() {
        OrderEntity order = order(OrderStatus.OPEN);
        GrowwOrderResponse response = new GrowwOrderResponse();
        response.setOrderStatus("OPEN");

        service.applyStatus(order, response);

        verify(auditService, never()).record(any(), any(), any(), any());
    }
}
