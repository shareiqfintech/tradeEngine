package com.example.trading.service;

import com.example.trading.entity.OrderEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OrderStatus;
import com.example.trading.exception.AuthenticationRequiredException;
import com.example.trading.exception.GrowwApiException;
import com.example.trading.exception.OrderRejectedException;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.groww.dto.GrowwOrderRequest;
import com.example.trading.groww.dto.GrowwOrderResponse;
import com.example.trading.config.TradingProperties;
import com.example.trading.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The one LIVE "submit this order to Groww and reconcile the result"
 * implementation, shared by the normal entry/switch path
 * ({@link TradingEngineService}) and the 15:10 safety-exit close path
 * ({@link SessionCloseService}) so there is no second copy of the
 * timeout / auth-failure / reject handling.
 *
 * <p>On a create-order timeout it queries {@code order_reference_id} status
 * instead of blindly retrying; it never submits the same order twice.
 * Callers do their own pre-checks (auth, kill switch, session cutoff) and
 * their own success auditing - this class only audits the failure paths
 * ({@code ORDER_REJECTED}) exactly as before.
 */
@Slf4j
@Service
public class OrderExecutionSupport {

    private static final String SEGMENT_FNO = "FNO";

    private final GrowwApiClient growwApiClient;
    private final GrowwOrderStatusService growwOrderStatusService;
    private final GrowwAuthenticationService growwAuthenticationService;
    private final AuditService auditService;
    private final OrderRepository orderRepository;
    private final TradingProperties properties;

    public OrderExecutionSupport(GrowwApiClient growwApiClient,
                                 GrowwOrderStatusService growwOrderStatusService,
                                 GrowwAuthenticationService growwAuthenticationService,
                                 AuditService auditService,
                                 OrderRepository orderRepository,
                                 TradingProperties properties) {
        this.growwApiClient = growwApiClient;
        this.growwOrderStatusService = growwOrderStatusService;
        this.growwAuthenticationService = growwAuthenticationService;
        this.auditService = auditService;
        this.orderRepository = orderRepository;
        this.properties = properties;
    }

    /**
     * Submits {@code order} to Groww using {@code userId}'s own access
     * token, applies the create response, then explicitly re-checks and
     * persists the authoritative broker status. Returns the updated order.
     *
     * @throws OrderRejectedException          Groww rejected it, or a timeout could not be reconciled
     * @throws AuthenticationRequiredException Groww rejected it with an auth error
     */
    public OrderEntity submitAndReconcile(Long userId, OrderEntity order, String exchange) {
        GrowwOrderRequest request = GrowwOrderRequest.builder()
                .tradingSymbol(order.getTradingSymbol())
                .quantity(order.getQuantity())
                .validity(properties.getOrder().getValidity())
                .exchange(exchange)
                .segment(SEGMENT_FNO)
                .product(order.getProduct())
                .orderType(properties.getOrder().getOrderType())
                .transactionType(order.getAction().name())
                .orderReferenceId(order.getOrderReferenceId())
                .build();

        GrowwOrderResponse response;
        try {
            response = growwApiClient.createOrder(userId, request);
        } catch (GrowwApiException ex) {
            if (isTimeout(ex)) {
                log.warn("ORDER_CREATE_TIMEOUT orderRef={} - querying reference status instead of retrying", order.getOrderReferenceId());
                response = recoverAfterTimeout(userId, order.getOrderReferenceId());
                if (response == null) {
                    order.setStatus(OrderStatus.FAILED);
                    order.setBrokerRemark("Timed out and could not reconcile via order_reference_id - requires manual review");
                    orderRepository.save(order);
                    auditService.record(AuditEventType.ORDER_REJECTED, order.getSignalId(), order.getOrderReferenceId(),
                            "Timeout with no reconciliation result");
                    throw new OrderRejectedException("Order create timed out for reference " + order.getOrderReferenceId()
                            + " and could not be reconciled");
                }
            } else if (ex.isAuthError()) {
                growwAuthenticationService.handleAuthFailureFromBroker(userId);
                order.setStatus(OrderStatus.FAILED);
                order.setBrokerRemark("Groww authentication failure while submitting order");
                orderRepository.save(order);
                throw new AuthenticationRequiredException("Groww rejected the order due to an authentication failure: " + ex.getMessage());
            } else {
                order.setStatus(OrderStatus.REJECTED);
                order.setBrokerRemark(ex.getMessage());
                orderRepository.save(order);
                auditService.record(AuditEventType.ORDER_REJECTED, order.getSignalId(), order.getOrderReferenceId(), ex.getMessage());
                throw new OrderRejectedException("Groww rejected the order: " + ex.getMessage());
            }
        }

        applyCreateResponse(order, response);

        // Explicitly re-check order status and persist the authoritative broker state.
        OrderEntity reconciled = growwOrderStatusService.refreshByGrowwOrderId(userId, order);
        return reconciled != null ? reconciled : order;
    }

    private GrowwOrderResponse recoverAfterTimeout(Long userId, String orderReferenceId) {
        try {
            return growwApiClient.getOrderStatusByReference(userId, orderReferenceId, SEGMENT_FNO);
        } catch (GrowwApiException ex) {
            log.error("ORDER_REFERENCE_RECONCILIATION_FAILED orderRef={} reason={}", orderReferenceId, ex.getMessage());
            return null;
        }
    }

    private void applyCreateResponse(OrderEntity order, GrowwOrderResponse response) {
        order.setGrowwOrderId(response.getGrowwOrderId());
        order.setStatus(growwOrderStatusService.mapStatus(response.getOrderStatus()));
        if (response.getFilledQuantity() != null) {
            order.setFilledQuantity(response.getFilledQuantity());
        }
        if (response.getAverageFillPrice() != null) {
            order.setAverageFillPrice(response.getAverageFillPrice());
        }
        if (response.getRemark() != null) {
            order.setBrokerRemark(response.getRemark());
        }
        orderRepository.save(order);
    }

    private boolean isTimeout(GrowwApiException ex) {
        return ex.getCause() instanceof java.util.concurrent.TimeoutException
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out"));
    }
}
