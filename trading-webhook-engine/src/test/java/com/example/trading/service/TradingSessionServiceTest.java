package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.enums.SessionState;
import com.example.trading.exception.TradingSessionClosedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins "now" with {@code Clock.fixed} and asserts the 09:25 / 15:10 / 15:30
 * IST boundaries and the restart-recovery state machine. 15:10 is the
 * SAFETY EXIT (new entries blocked); 15:30 is the official market close.
 */
class TradingSessionServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private TradingStateService tradingStateService;
    private AuditService auditService;

    /** Builds a session service whose clock is pinned to {@code istInstant} - clock zone is deliberately UTC to prove IST is used regardless. */
    private TradingSessionService at(String istLocalDateTime) {
        tradingStateService = mock(TradingStateService.class);
        auditService = mock(AuditService.class);
        ZonedDateTime ist = ZonedDateTime.of(java.time.LocalDateTime.parse(istLocalDateTime), IST);
        Clock clock = Clock.fixed(ist.toInstant(), ZoneOffset.UTC); // server zone UTC on purpose
        // The service must re-zone to Asia/Kolkata itself:
        Clock istClock = Clock.fixed(ist.toInstant(), IST);
        return new TradingSessionService(istClock, new TradingProperties(), tradingStateService, auditService);
    }

    // 2026-09-10 is a Thursday.
    private static final String THU = "2026-09-10T";

    @Test
    void at_0924_59_newEntryRejected_sessionNotStarted() {
        assertThatThrownBy(() -> at(THU + "09:24:59").assertNewEntryAllowed())
                .isInstanceOf(TradingSessionClosedException.class)
                .satisfies(e -> assertThat(((TradingSessionClosedException) e).getReasonCode())
                        .isEqualTo(TradingSessionClosedException.SESSION_NOT_STARTED));
    }

    @Test
    void at_0925_00_newEntryAllowed() {
        assertThatCode(() -> at(THU + "09:25:00").assertNewEntryAllowed()).doesNotThrowAnyException();
    }

    @Test
    void at_1200_newEntryAllowed() {
        assertThatCode(() -> at(THU + "12:00:00").assertNewEntryAllowed()).doesNotThrowAnyException();
    }

    @Test
    void at_1509_59_newEntryAllowed() {
        assertThatCode(() -> at(THU + "15:09:59").assertNewEntryAllowed()).doesNotThrowAnyException();
    }

    @Test
    void at_1510_00_newEntryRejected_tradingCutoff() {
        assertThatThrownBy(() -> at(THU + "15:10:00").assertNewEntryAllowed())
                .isInstanceOf(TradingSessionClosedException.class)
                .satisfies(e -> assertThat(((TradingSessionClosedException) e).getReasonCode())
                        .isEqualTo(TradingSessionClosedException.SESSION_TRADING_CUTOFF));
    }

    @Test
    void at_1510_30_newEntryRejected_tradingCutoff() {
        assertThatThrownBy(() -> at(THU + "15:10:30").assertNewEntryAllowed())
                .satisfies(e -> assertThat(((TradingSessionClosedException) e).getReasonCode())
                        .isEqualTo(TradingSessionClosedException.SESSION_TRADING_CUTOFF));
    }

    @Test
    void at_1515_and_1529_newEntryRejected() {
        assertThatThrownBy(() -> at(THU + "15:15:00").assertNewEntryAllowed()).isInstanceOf(TradingSessionClosedException.class);
        assertThatThrownBy(() -> at(THU + "15:29:59").assertNewEntryAllowed())
                .satisfies(e -> assertThat(((TradingSessionClosedException) e).getReasonCode())
                        .isEqualTo(TradingSessionClosedException.SESSION_TRADING_CUTOFF));
    }

    @Test
    void at_1530_00_marketClosed() {
        assertThatThrownBy(() -> at(THU + "15:30:00").assertNewEntryAllowed())
                .satisfies(e -> assertThat(((TradingSessionClosedException) e).getReasonCode())
                        .isEqualTo(TradingSessionClosedException.MARKET_CLOSED));
    }

    @Test
    void weekend_newEntryRejected_bothDays() {
        assertThatThrownBy(() -> at("2026-09-12T12:00:00").assertNewEntryAllowed()) // Saturday
                .isInstanceOf(TradingSessionClosedException.class);
        assertThatThrownBy(() -> at("2026-09-13T12:00:00").assertNewEntryAllowed()) // Sunday
                .isInstanceOf(TradingSessionClosedException.class);
    }

    @Test
    void decisionIsBasedOnIstWallTime_notTheInstantsUtcTime() {
        // This instant is 09:55 UTC == 15:25 IST. The rules run in IST, so this
        // must be AFTER the 15:10 cutoff (SESSION_TRADING_CUTOFF), even though
        // 09:55 "looks" like the morning in UTC.
        java.time.Instant instant = ZonedDateTime.of(java.time.LocalDateTime.parse("2026-09-10T09:55:00"), ZoneOffset.UTC).toInstant();
        TradingSessionService svc = new TradingSessionService(
                Clock.fixed(instant, IST), new TradingProperties(), mock(TradingStateService.class), mock(AuditService.class));
        assertThatThrownBy(svc::assertNewEntryAllowed)
                .satisfies(e -> assertThat(((TradingSessionClosedException) e).getReasonCode())
                        .isEqualTo(TradingSessionClosedException.SESSION_TRADING_CUTOFF));
    }

    @Test
    void isNewEntryAllowed_falseWhenKillSwitchOrPaused_evenInsideWindow() {
        TradingSessionService svc = at(THU + "12:00:00");
        assertThat(svc.isNewEntryAllowed()).isTrue();

        org.mockito.Mockito.when(tradingStateService.isKillSwitchEnabled()).thenReturn(true);
        assertThat(svc.isNewEntryAllowed()).isFalse();
    }

    // ---- restart recovery ----

    @Test
    void restartAt_0930_landsInTradingActive() {
        TradingSessionService svc = at(THU + "09:30:00");
        assertThat(svc.recomputeStateOnStartup()).isEqualTo(SessionState.TRADING_ACTIVE);
        verify(tradingStateService).openTradingWindow();
    }

    @Test
    void restartAt_1505_stillWithinWindow_tradingActive() {
        // NOTE: the spec's "restart at 15:05 -> no new entries" bullet contradicts its
        // own authoritative 15:10 cutoff ("15:09:59 -> allowed", "15:10:00 -> rejected").
        // The 15:10 rule wins: 15:05 is still inside the trading window.
        TradingSessionService svc = at(THU + "15:05:00");
        assertThat(svc.recomputeStateOnStartup()).isEqualTo(SessionState.TRADING_ACTIVE);
    }

    @Test
    void restartAt_1512_landsInSessionClosing() {
        TradingSessionService svc = at(THU + "15:12:00");
        assertThat(svc.recomputeStateOnStartup()).isEqualTo(SessionState.SESSION_CLOSING);
    }

    @Test
    void restartAt_1520_landsInSessionClosing_neverReopensTrading() {
        TradingSessionService svc = at(THU + "15:20:00");
        assertThat(svc.recomputeStateOnStartup()).isEqualTo(SessionState.SESSION_CLOSING);
    }

    @Test
    void restartAt_1535_landsInMarketClosed() {
        TradingSessionService svc = at(THU + "15:35:00");
        assertThat(svc.recomputeStateOnStartup()).isEqualTo(SessionState.MARKET_CLOSED);
    }

    @Test
    void restartAt_0800_landsInPreMarket() {
        TradingSessionService svc = at(THU + "08:00:00");
        assertThat(svc.recomputeStateOnStartup()).isEqualTo(SessionState.PRE_MARKET);
    }

    @Test
    void lifecycleTransitions_areAudited() {
        TradingSessionService svc = at(THU + "12:00:00");
        svc.activateTradingSession();
        assertThat(svc.getLifecycleState()).isEqualTo(SessionState.TRADING_ACTIVE);
        verify(auditService).record(org.mockito.ArgumentMatchers.eq(com.example.trading.enums.AuditEventType.SESSION_STARTED),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        svc.beginSessionClosing();
        assertThat(svc.getLifecycleState()).isEqualTo(SessionState.SESSION_CLOSING);
        svc.markSafetyExitComplete();
        assertThat(svc.getLifecycleState()).isEqualTo(SessionState.SAFETY_EXIT_COMPLETED);
        svc.markOfficialMarketClosed();
        assertThat(svc.getLifecycleState()).isEqualTo(SessionState.MARKET_CLOSED);
    }

    @Test
    void effectiveState_killSwitchAndPauseOverlayTheLifecycle() {
        TradingSessionService svc = at(THU + "12:00:00");
        svc.activateTradingSession();

        org.mockito.Mockito.when(tradingStateService.isPaused()).thenReturn(true);
        assertThat(svc.getEffectiveState(true)).isEqualTo(SessionState.PAUSED);

        org.mockito.Mockito.when(tradingStateService.isKillSwitchEnabled()).thenReturn(true);
        assertThat(svc.getEffectiveState(true)).isEqualTo(SessionState.KILL_SWITCH);
    }
}
