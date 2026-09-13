package com.example.trading.controller;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.*;
import com.example.trading.entity.AuditEventEntity;
import com.example.trading.entity.OrderEntity;
import com.example.trading.entity.TradingSignalEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OrderStatus;
import com.example.trading.enums.SignalStatus;
import com.example.trading.enums.TradingAction;
import com.example.trading.enums.TargetStatus;
import com.example.trading.repository.AuditEventRepository;
import com.example.trading.repository.OrderRepository;
import com.example.trading.repository.PositionTargetRepository;
import com.example.trading.repository.TradingSignalRepository;
import com.example.trading.security.AuthenticatedUser;
import com.example.trading.service.GrowwPositionService;
import com.example.trading.service.RiskManagementService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Read-only reporting endpoints for the operator console (frontend). These
 * were not part of the original backend build - they exist purely to
 * expose data that already existed server-side (trading_signal, orders,
 * audit_event tables; GrowwPositionService; RiskManagementService's usage
 * counters) over HTTP. Nothing here places, modifies, or cancels an order,
 * changes trading state, or evaluates risk - that all remains exclusively
 * in TradingEngineService/RiskManagementService/TradingAdminController.
 */
@RestController
@RequestMapping("/api/trading")
public class TradingQueryController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TradingSignalRepository signalRepository;
    private final OrderRepository orderRepository;
    private final AuditEventRepository auditEventRepository;
    private final PositionTargetRepository positionTargetRepository;
    private final GrowwPositionService growwPositionService;
    private final RiskManagementService riskManagementService;
    private final TradingProperties tradingProperties;

    public TradingQueryController(TradingSignalRepository signalRepository,
                                   OrderRepository orderRepository,
                                   AuditEventRepository auditEventRepository,
                                   PositionTargetRepository positionTargetRepository,
                                   GrowwPositionService growwPositionService,
                                   RiskManagementService riskManagementService,
                                   TradingProperties tradingProperties) {
        this.signalRepository = signalRepository;
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.positionTargetRepository = positionTargetRepository;
        this.growwPositionService = growwPositionService;
        this.riskManagementService = riskManagementService;
        this.tradingProperties = tradingProperties;
    }

    // ------------------------------------------------------------------
    // Signals
    // ------------------------------------------------------------------

    @GetMapping("/signals")
    public PageResponse<TradingSignalResponse> listSignals(
            @RequestParam(required = false) TradingAction action,
            @RequestParam(required = false) SignalStatus status,
            @RequestParam(required = false) String underlying,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        Specification<TradingSignalEntity> spec = Specification.where(null);
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (underlying != null && !underlying.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("underlying"), underlying.toUpperCase(Locale.ROOT)));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("signalId")), like),
                    cb.like(cb.lower(root.get("underlying")), like)));
        }
        spec = applyDateRange(spec, from, to);

        Pageable pageable = PageRequest.of(page, boundedSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<TradingSignalEntity> result = signalRepository.findAll(spec, pageable);

        return PageResponse.of(result.map(TradingSignalResponse::from));
    }

    @GetMapping("/signals/{signalId}")
    public TradingSignalDetailResponse getSignal(@PathVariable String signalId) {
        TradingSignalEntity entity = signalRepository.findBySignalId(signalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No signal found for signalId=" + signalId));

        List<AuditEventResponse> auditTrail = auditEventRepository.findBySignalIdOrderByCreatedAtAsc(signalId).stream()
                .map(AuditEventResponse::from)
                .toList();

        TradingOrderResponse order = orderRepository.findFirstBySignalIdOrderByCreatedAtDesc(signalId)
                .map(TradingOrderResponse::from)
                .orElse(null);

        return TradingSignalDetailResponse.from(TradingSignalResponse.from(entity), auditTrail, order);
    }

    // ------------------------------------------------------------------
    // Orders
    // ------------------------------------------------------------------

    @GetMapping("/orders")
    public PageResponse<TradingOrderResponse> listOrders(
            @RequestParam(required = false) TradingAction action,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String tradingSymbol,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        // NOTE: `mode` (PAPER/LIVE) is intentionally NOT filtered on - the
        // `orders` table has no such column (mode is a global, in-memory
        // config value at the time an order was placed, never persisted
        // per-order). Accepted-but-ignored rather than erroring, since a
        // non-functional filter is safer than a fabricated one. See
        // frontend/README.md "API contract assumptions".

        Specification<OrderEntity> spec = Specification.where(null);
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (tradingSymbol != null && !tradingSymbol.isBlank()) {
            String like = "%" + tradingSymbol.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("tradingSymbol")), like));
        }
        spec = applyDateRange(spec, from, to);

        Pageable pageable = PageRequest.of(page, boundedSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<OrderEntity> result = orderRepository.findAll(spec, pageable);

        return PageResponse.of(result.map(TradingOrderResponse::from));
    }

    @GetMapping("/orders/{id}")
    public TradingOrderDetailResponse getOrder(@PathVariable Long id) {
        OrderEntity entity = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order found for id=" + id));

        List<AuditEventResponse> auditTrail = auditEventRepository.findByOrderReferenceIdOrderByCreatedAtAsc(entity.getOrderReferenceId())
                .stream()
                .map(AuditEventResponse::from)
                .toList();

        TradingSignalResponse signal = signalRepository.findBySignalId(entity.getSignalId())
                .map(TradingSignalResponse::from)
                .orElse(null);

        return TradingOrderDetailResponse.from(TradingOrderResponse.from(entity), auditTrail, signal);
    }

    // ------------------------------------------------------------------
    // Positions
    // ------------------------------------------------------------------

    @GetMapping("/positions")
    public ResponseEntity<List<com.example.trading.dto.Position>> listPositions(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(growwPositionService.getPositions(user.id()));
    }

    /**
     * Automatic profit-target rows for the calling user - Target Points
     * (config), the ACTUAL Groww entry price, the backend-calculated Target
     * Price, the last observed LTP, and the monitor status. Read-only; never
     * any Groww credential.
     */
    @GetMapping("/positions/targets")
    public ResponseEntity<List<PositionTargetResponse>> listPositionTargets(@AuthenticationPrincipal AuthenticatedUser user) {
        List<PositionTargetResponse> targets = positionTargetRepository.findByUserIdOrderByUpdatedAtDesc(user.id()).stream()
                .filter(pt -> pt.getTargetStatus() != TargetStatus.CLOSED
                        || (pt.getClosedAt() != null && pt.getClosedAt().isAfter(Instant.now().minusSeconds(24 * 3600))))
                .map(PositionTargetResponse::from)
                .toList();
        return ResponseEntity.ok(targets);
    }

    // ------------------------------------------------------------------
    // Audit
    // ------------------------------------------------------------------

    @GetMapping("/audit")
    public PageResponse<AuditEventResponse> listAudit(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) String signalId,
            @RequestParam(required = false) String orderReferenceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        Specification<AuditEventEntity> spec = Specification.where(null);
        if (eventType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eventType"), eventType));
        }
        if (signalId != null && !signalId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("signalId"), signalId));
        }
        if (orderReferenceId != null && !orderReferenceId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("orderReferenceId"), orderReferenceId));
        }
        spec = applyDateRange(spec, from, to);

        Pageable pageable = PageRequest.of(page, boundedSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<AuditEventEntity> result = auditEventRepository.findAll(spec, pageable);

        return PageResponse.of(result.map(AuditEventResponse::from));
    }

    // ------------------------------------------------------------------
    // Risk
    // ------------------------------------------------------------------

    @GetMapping("/risk")
    public RiskStatusResponse getRisk(@AuthenticationPrincipal AuthenticatedUser user) {
        return RiskStatusResponse.from(riskManagementService.getUsageSnapshot(user.id()));
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    @GetMapping("/config")
    public TradingConfigResponse getConfig() {
        return TradingConfigResponse.from(tradingProperties);
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private <T> Specification<T> applyDateRange(Specification<T> spec, Instant from, Instant to) {
        Optional<Instant> fromOpt = Optional.ofNullable(from);
        Optional<Instant> toOpt = Optional.ofNullable(to);
        if (fromOpt.isPresent()) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromOpt.get()));
        }
        if (toOpt.isPresent()) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toOpt.get()));
        }
        return spec;
    }

    private int boundedSize(int size) {
        if (size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, 100);
    }
}
