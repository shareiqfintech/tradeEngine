package com.example.trading.scheduler;

import com.example.trading.enums.SessionState;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.service.GrowwUserResolver;
import com.example.trading.service.SessionCloseService;
import com.example.trading.service.TradingSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * Drives the trading-session lifecycle for one IST day, all crons pinned to
 * {@code Asia/Kolkata} (never the server/EC2 zone):
 *
 * <ul>
 *   <li><b>09:25</b> {@code activateTradingSession()} - PRE_MARKET -&gt; TRADING_ACTIVE, new entries allowed</li>
 *   <li><b>15:10</b> {@code startSafetyPositionClose()} - SAFETY EXIT: stop new
 *       entries and auto-close every system-managed position (NOT the market close)</li>
 *   <li><b>15:30</b> {@code markOfficialMarketClosed()} - official NSE close; the JAR keeps running</li>
 * </ul>
 *
 * <p>The schedulers are <b>not</b> the only protection: {@code TradingEngineService}
 * independently asserts the current IST time immediately before every new
 * entry order. A 60s monitor and an on-startup recompute make restarts at
 * any time of day land in the correct state.
 */
@Slf4j
@Component
public class TradingWindowScheduler {

    private final TradingSessionService tradingSessionService;
    private final SessionCloseService sessionCloseService;
    private final GrowwAuthenticationService growwAuthenticationService;
    private final GrowwUserResolver growwUserResolver;

    public TradingWindowScheduler(TradingSessionService tradingSessionService,
                                   SessionCloseService sessionCloseService,
                                   GrowwAuthenticationService growwAuthenticationService,
                                   GrowwUserResolver growwUserResolver) {
        this.tradingSessionService = tradingSessionService;
        this.sessionCloseService = sessionCloseService;
        this.growwAuthenticationService = growwAuthenticationService;
        this.growwUserResolver = growwUserResolver;
    }

    /** On startup: land in the state the clock says we should be in, and continue any in-progress safety exit. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        SessionState state = tradingSessionService.recomputeStateOnStartup();
        if (state == SessionState.SESSION_CLOSING) {
            log.warn("STARTUP_IN_SAFETY_EXIT_WINDOW - running system-managed position close");
            sessionCloseService.startSafetyPositionClose();
        }
    }

    @Scheduled(cron = "0 25 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void activateTradingSession() {
        List<Long> connectedUserIds = growwUserResolver.findAllConnectedUserIds();
        boolean anyAuthenticated = connectedUserIds.stream().anyMatch(growwAuthenticationService::isAuthenticated);
        if (!anyAuthenticated) {
            log.warn("SESSION_ACTIVATE_NO_AUTHENTICATED_USER connectedUsers={} - activating anyway, entries still gated by per-user auth", connectedUserIds.size());
        }
        tradingSessionService.activateTradingSession();
    }

    @Scheduled(cron = "0 10 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void startSafetyPositionClose() {
        sessionCloseService.startSafetyPositionClose();
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void markOfficialMarketClosed() {
        tradingSessionService.markOfficialMarketClosed();
    }

    /**
     * Every minute: catch-up activation if 09:25 was missed, continue the
     * safety exit through 15:10-15:30, and force MARKET_CLOSED after 15:30.
     */
    @Scheduled(fixedRate = 60_000)
    public void monitor() {
        if (!tradingSessionService.isTradingDay(tradingSessionService.todayIst())) {
            return;
        }
        LocalTime now = tradingSessionService.nowIst().toLocalTime();
        SessionState state = tradingSessionService.getLifecycleState();

        if (!now.isBefore(tradingSessionService.officialClose())) {
            if (state != SessionState.MARKET_CLOSED) {
                tradingSessionService.markOfficialMarketClosed();
            }
            return;
        }
        if (!now.isBefore(tradingSessionService.tradingCutoff())) {
            if (state == SessionState.SESSION_CLOSING) {
                sessionCloseService.monitorSafetyExit();
            } else if (state != SessionState.SAFETY_EXIT_COMPLETED) {
                sessionCloseService.startSafetyPositionClose();
            }
            return;
        }
        if (!now.isBefore(tradingSessionService.sessionStart()) && state == SessionState.PRE_MARKET) {
            tradingSessionService.activateTradingSession();
        }
    }
}
