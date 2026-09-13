package com.example.trading.scheduler;

import com.example.trading.enums.SessionState;
import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.service.GrowwUserResolver;
import com.example.trading.service.SessionCloseService;
import com.example.trading.service.TradingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The 09:25 / 15:10 / 15:30 IST crons drive {@link TradingSessionService} /
 * {@link SessionCloseService}; the 60s monitor and on-startup hook keep a
 * restart landing in the right state. Duplicate 15:10 fires delegate to the
 * per-day Redis lock inside {@code SessionCloseService} (one close process).
 */
class TradingWindowSchedulerTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private TradingSessionService tradingSessionService;
    private SessionCloseService sessionCloseService;
    private GrowwAuthenticationService growwAuthenticationService;
    private GrowwUserResolver growwUserResolver;
    private TradingWindowScheduler scheduler;

    @BeforeEach
    void setUp() {
        tradingSessionService = mock(TradingSessionService.class);
        sessionCloseService = mock(SessionCloseService.class);
        growwAuthenticationService = mock(GrowwAuthenticationService.class);
        growwUserResolver = mock(GrowwUserResolver.class);
        scheduler = new TradingWindowScheduler(tradingSessionService, sessionCloseService,
                growwAuthenticationService, growwUserResolver);

        when(growwUserResolver.findAllConnectedUserIds()).thenReturn(List.of(1L));
        when(growwAuthenticationService.isAuthenticated(1L)).thenReturn(true);
        when(tradingSessionService.isTradingDay(any())).thenReturn(true);
        when(tradingSessionService.todayIst()).thenReturn(LocalDate.of(2026, 9, 10));
        when(tradingSessionService.sessionStart()).thenReturn(LocalTime.of(9, 25));
        when(tradingSessionService.tradingCutoff()).thenReturn(LocalTime.of(15, 10));
        when(tradingSessionService.officialClose()).thenReturn(LocalTime.of(15, 30));
    }

    private void nowIst(String hhmmss) {
        when(tradingSessionService.nowIst())
                .thenReturn(ZonedDateTime.of(java.time.LocalDate.of(2026, 9, 10), LocalTime.parse(hhmmss), IST));
    }

    @Test
    void cron_0925_activatesSession() {
        scheduler.activateTradingSession();
        verify(tradingSessionService).activateTradingSession();
    }

    @Test
    void cron_1510_startsSafetyPositionClose() {
        scheduler.startSafetyPositionClose();
        verify(sessionCloseService).startSafetyPositionClose();
    }

    @Test
    void cron_1510_firedTwice_bothDelegate_lockDedupesInsideService() {
        scheduler.startSafetyPositionClose();
        scheduler.startSafetyPositionClose();
        verify(sessionCloseService, times(2)).startSafetyPositionClose();
        // de-duplication is the per-day Redis lock inside SessionCloseService (covered by SessionCloseServiceTest).
    }

    @Test
    void cron_1530_marksOfficialMarketClosed() {
        scheduler.markOfficialMarketClosed();
        verify(tradingSessionService).markOfficialMarketClosed();
    }

    @Test
    void monitor_between1510and1530_whileSessionClosing_runsMonitorSafetyExit() {
        nowIst("15:20:00");
        when(tradingSessionService.getLifecycleState()).thenReturn(SessionState.SESSION_CLOSING);
        scheduler.monitor();
        verify(sessionCloseService).monitorSafetyExit();
    }

    @Test
    void monitor_after1530_forcesMarketClosed() {
        nowIst("15:31:00");
        when(tradingSessionService.getLifecycleState()).thenReturn(SessionState.SESSION_CLOSING);
        scheduler.monitor();
        verify(tradingSessionService).markOfficialMarketClosed();
    }

    @Test
    void monitor_insideWindow_stillPreMarket_catchesUpActivation() {
        nowIst("09:40:00");
        when(tradingSessionService.getLifecycleState()).thenReturn(SessionState.PRE_MARKET);
        scheduler.monitor();
        verify(tradingSessionService).activateTradingSession();
    }

    @Test
    void monitor_weekend_isNoOp() {
        when(tradingSessionService.isTradingDay(any())).thenReturn(false);
        scheduler.monitor();
        verify(tradingSessionService, never()).activateTradingSession();
        verify(sessionCloseService, never()).monitorSafetyExit();
    }

    @Test
    void onStartup_inSafetyExitWindow_runsClose() {
        when(tradingSessionService.recomputeStateOnStartup()).thenReturn(SessionState.SESSION_CLOSING);
        scheduler.onStartup();
        verify(sessionCloseService).startSafetyPositionClose();
    }

    @Test
    void onStartup_preMarket_doesNotRunClose() {
        when(tradingSessionService.recomputeStateOnStartup()).thenReturn(SessionState.PRE_MARKET);
        scheduler.onStartup();
        verify(sessionCloseService, never()).startSafetyPositionClose();
    }
}
