package com.example.trading.controller;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.GrowwAuthCheckResponse;
import com.example.trading.dto.Position;
import com.example.trading.dto.TradingStatusResponse;
import com.example.trading.dto.WebhookResponse;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.repository.OrderRepository;
import com.example.trading.security.AuthenticatedUser;
import com.example.trading.service.GrowwPositionService;
import com.example.trading.service.TradingSessionService;
import com.example.trading.service.TradingStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Operator control surface: kill switch, pause/resume, and a read-only
 * status snapshot. None of these endpoints ever place, modify, or cancel an
 * order themselves - they only flip the flags {@code TradingEngineService}
 * checks before doing so.
 */
@Slf4j
@RestController
@RequestMapping("/api/trading")
public class TradingAdminController {

    private final TradingStateService tradingStateService;
    private final TradingSessionService tradingSessionService;
    private final GrowwAuthenticationService growwAuthenticationService;
    private final OrderRepository orderRepository;
    private final GrowwPositionService growwPositionService;
    private final TradingProperties properties;

    public TradingAdminController(TradingStateService tradingStateService,
                                   TradingSessionService tradingSessionService,
                                   GrowwAuthenticationService growwAuthenticationService,
                                   OrderRepository orderRepository,
                                   GrowwPositionService growwPositionService,
                                   TradingProperties properties) {
        this.tradingStateService = tradingStateService;
        this.tradingSessionService = tradingSessionService;
        this.growwAuthenticationService = growwAuthenticationService;
        this.orderRepository = orderRepository;
        this.growwPositionService = growwPositionService;
        this.properties = properties;
    }

    @PostMapping("/kill-switch/enable")
    public ResponseEntity<WebhookResponse> enableKillSwitch() {
        tradingStateService.enableKillSwitch();
        return ResponseEntity.ok(new WebhookResponse("KILL_SWITCH_ENABLED", null));
    }

    @PostMapping("/kill-switch/disable")
    public ResponseEntity<WebhookResponse> disableKillSwitch() {
        tradingStateService.disableKillSwitch();
        return ResponseEntity.ok(new WebhookResponse("KILL_SWITCH_DISABLED", null));
    }

    @PostMapping("/pause")
    public ResponseEntity<WebhookResponse> pause() {
        tradingStateService.pause();
        return ResponseEntity.ok(new WebhookResponse("PAUSED", null));
    }

    @PostMapping("/resume")
    public ResponseEntity<WebhookResponse> resume() {
        tradingStateService.resume();
        return ResponseEntity.ok(new WebhookResponse("RESUMED", null));
    }

    /**
     * Manual "authenticate now" trigger. Outside of this, Groww
     * authentication only happens via GrowwAuthenticationScheduler
     * (08:00-09:15 IST) or as a side effect of processing a signal during
     * market hours - this exists so an operator can verify API
     * key/TOTP-secret configuration on demand, without waiting for either.
     * Calls the exact same {@code GrowwAuthenticationService.authenticate()}
     * used by both of those paths - no separate/parallel auth logic.
     */
    /** Re-authenticates the CALLING user's own Groww account - resolved from their session, never a global/shared account. */
    @PostMapping("/groww/authenticate")
    public ResponseEntity<GrowwAuthCheckResponse> authenticateGroww(@AuthenticationPrincipal AuthenticatedUser user) {
        boolean authenticated = growwAuthenticationService.authenticate(user.id());
        return ResponseEntity.ok(new GrowwAuthCheckResponse(authenticated, growwAuthenticationService.getState(user.id())));
    }

    /** Reflects the CALLING user's own Groww authentication/position state - never another user's. */
    @GetMapping("/status")
    public ResponseEntity<TradingStatusResponse> status(@AuthenticationPrincipal AuthenticatedUser user) {
        boolean growwAuthenticated = growwAuthenticationService.isAuthenticated(user.id());
        TradingStatusResponse response = TradingStatusResponse.builder()
                .mode(properties.getMode())
                .tradingEnabled(tradingSessionService.isNewEntryAllowed())
                .growwAuthenticated(growwAuthenticated)
                .killSwitch(tradingStateService.isKillSwitchEnabled())
                .paused(tradingStateService.isPaused())
                .ordersToday((int) ordersToday())
                .openPositions(openPositions(user.id()))
                .sessionState(tradingSessionService.getEffectiveState(growwAuthenticated).name())
                .sessionStart(properties.getMarket().getStart())
                .tradingCutoff(properties.getMarket().getTradingCutoff())
                .officialMarketClose(properties.getMarket().getClose())
                .timezone(properties.getTimezone())
                .build();
        return ResponseEntity.ok(response);
    }

    private long ordersToday() {
        ZoneId zone = properties.getZoneId();
        Instant start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        return orderRepository.countByCreatedAtBetween(start, end);
    }

    private int openPositions(Long userId) {
        if (!growwAuthenticationService.isAuthenticated(userId)) {
            return 0;
        }
        try {
            List<Position> positions = growwPositionService.getPositions(userId);
            return (int) positions.stream().filter(p -> p.getNetQuantity() != null && p.getNetQuantity() != 0).count();
        } catch (Exception ex) {
            log.warn("STATUS_OPEN_POSITIONS_LOOKUP_FAILED reason={}", ex.getMessage());
            return 0;
        }
    }
}
