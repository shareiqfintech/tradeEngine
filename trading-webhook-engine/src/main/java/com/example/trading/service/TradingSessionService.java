package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.SessionState;
import com.example.trading.exception.TradingSessionClosedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the trading-session lifecycle and every time-of-day decision, all in
 * {@code trading.timezone} (Asia/Kolkata), read through an injected
 * {@link Clock} so the rules are correct regardless of the host/EC2 zone
 * and can be pinned in tests.
 *
 * <p>Three distinct times:
 * <ul>
 *   <li><b>09:25</b> {@code market.start} - session opens, new entries allowed</li>
 *   <li><b>15:10</b> {@code market.trading-cutoff} - SAFETY EXIT: stop new
 *       entries and auto-close system-managed positions. <b>NOT</b> the market close.</li>
 *   <li><b>15:30</b> {@code market.close} - official NSE close; everything stays
 *       disabled, the JAR keeps running</li>
 * </ul>
 *
 * <p>{@link #assertNewEntryAllowed()} is the mandatory guard called
 * immediately before every new-entry order is dispatched (PAPER simulate or
 * Groww create-order), so a signal delayed in the async queue past 15:10
 * still never opens a position. Exit / position-close orders are never
 * gated by it.
 */
@Slf4j
@Service
public class TradingSessionService {

    /** Pure time-window classification for a NEW entry (kill switch / pause handled separately by the engine). */
    public enum EntryWindow {
        OPEN,
        BEFORE_START,
        AFTER_CUTOFF,
        MARKET_CLOSED
    }

    private final Clock clock;
    private final TradingProperties properties;
    private final TradingStateService tradingStateService;
    private final AuditService auditService;

    private final AtomicReference<SessionState> lifecycle = new AtomicReference<>(SessionState.PRE_MARKET);

    public TradingSessionService(Clock clock,
                                 TradingProperties properties,
                                 TradingStateService tradingStateService,
                                 AuditService auditService) {
        this.clock = clock;
        this.properties = properties;
        this.tradingStateService = tradingStateService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Clock - the single source of "now", always Asia/Kolkata
    // ------------------------------------------------------------------

    public ZonedDateTime nowIst() {
        return ZonedDateTime.now(clock);
    }

    public LocalDate todayIst() {
        return LocalDate.now(clock);
    }

    public LocalTime sessionStart() {
        return properties.getMarket().startTime();
    }

    public LocalTime tradingCutoff() {
        return properties.getMarket().tradingCutoffTime();
    }

    public LocalTime officialClose() {
        return properties.getMarket().closeTime();
    }

    /**
     * Monday-Friday for now. NSE trading-holiday support is not implemented;
     * this is the single extension point - plug a holiday calendar in here
     * and the whole session machine honours it.
     */
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    // ------------------------------------------------------------------
    // NEW-entry window
    // ------------------------------------------------------------------

    public EntryWindow classifyEntryWindow() {
        ZonedDateTime now = nowIst();
        if (!isTradingDay(now.toLocalDate())) {
            return EntryWindow.BEFORE_START;
        }
        LocalTime t = now.toLocalTime();
        if (t.isBefore(sessionStart())) {
            return EntryWindow.BEFORE_START;
        }
        if (!t.isBefore(officialClose())) {
            return EntryWindow.MARKET_CLOSED;
        }
        if (!t.isBefore(tradingCutoff())) {
            return EntryWindow.AFTER_CUTOFF;
        }
        return EntryWindow.OPEN;
    }

    /** True only when a brand-new entry may be opened right now: window OPEN and no kill switch / pause. */
    public boolean isNewEntryAllowed() {
        return classifyEntryWindow() == EntryWindow.OPEN
                && !tradingStateService.isKillSwitchEnabled()
                && !tradingStateService.isPaused();
    }

    /**
     * Empty when a new entry is allowed by the clock; otherwise the
     * rejection reason code the webhook / engine must record
     * ({@code SESSION_NOT_STARTED} / {@code SESSION_TRADING_CUTOFF}).
     * Kill switch / pause are intentionally NOT considered here - those
     * signals still reach the engine and are rejected there with their own
     * existing reason codes.
     */
    public java.util.Optional<String> classifyNewEntry() {
        return switch (classifyEntryWindow()) {
            case OPEN -> java.util.Optional.empty();
            case BEFORE_START -> java.util.Optional.of(TradingSessionClosedException.SESSION_NOT_STARTED);
            case AFTER_CUTOFF -> java.util.Optional.of(TradingSessionClosedException.SESSION_TRADING_CUTOFF);
            case MARKET_CLOSED -> java.util.Optional.of(TradingSessionClosedException.MARKET_CLOSED);
        };
    }

    /**
     * MANDATORY final guard - call immediately before dispatching any NEW
     * entry order (PAPER simulate or Groww create-order). Throws if "now"
     * (Asia/Kolkata) is before 09:25, at/after 15:10, or at/after 15:30.
     */
    public void assertNewEntryAllowed() {
        switch (classifyEntryWindow()) {
            case OPEN -> { /* allowed */ }
            case BEFORE_START -> throw new TradingSessionClosedException(
                    TradingSessionClosedException.SESSION_NOT_STARTED,
                    "New entries are not allowed before " + properties.getMarket().getStart()
                            + " IST (or on a non-trading day) - now=" + nowIst());
            case AFTER_CUTOFF -> throw new TradingSessionClosedException(
                    TradingSessionClosedException.SESSION_TRADING_CUTOFF,
                    "New entries are blocked at/after the " + properties.getMarket().getTradingCutoff()
                            + " IST safety cutoff - now=" + nowIst());
            case MARKET_CLOSED -> throw new TradingSessionClosedException(
                    TradingSessionClosedException.MARKET_CLOSED,
                    "Official market close (" + properties.getMarket().getClose() + " IST) has passed - now=" + nowIst());
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle state machine
    // ------------------------------------------------------------------

    public SessionState getLifecycleState() {
        return lifecycle.get();
    }

    /**
     * The value the status API reports for {@code sessionState}: kill switch
     * / pause / auth-required are higher-priority display overlays on top of
     * the time-driven lifecycle (same priority order the frontend already
     * uses).
     */
    public SessionState getEffectiveState(boolean growwAuthenticated) {
        if (tradingStateService.isKillSwitchEnabled()) {
            return SessionState.KILL_SWITCH;
        }
        if (tradingStateService.isPaused()) {
            return SessionState.PAUSED;
        }
        SessionState current = lifecycle.get();
        if (!growwAuthenticated && current == SessionState.TRADING_ACTIVE) {
            return SessionState.AUTH_REQUIRED;
        }
        return current;
    }

    /** 09:25 (and restart catch-up): PRE_MARKET -> TRADING_ACTIVE. Idempotent; no-op outside the window. */
    public void activateTradingSession() {
        if (!isTradingDay(todayIst())) {
            log.info("SESSION_ACTIVATE_SKIPPED reason=NON_TRADING_DAY date={}", todayIst());
            return;
        }
        EntryWindow window = classifyEntryWindow();
        if (window != EntryWindow.OPEN) {
            log.info("SESSION_ACTIVATE_SKIPPED reason=OUTSIDE_ENTRY_WINDOW window={}", window);
            return;
        }
        SessionState previous = lifecycle.getAndSet(SessionState.TRADING_ACTIVE);
        tradingStateService.openTradingWindow();
        if (previous != SessionState.TRADING_ACTIVE) {
            auditService.record(AuditEventType.SESSION_STARTED, null, null,
                    "sessionDate=" + todayIst() + " start=" + properties.getMarket().getStart()
                            + " tradingCutoff=" + properties.getMarket().getTradingCutoff()
                            + " officialClose=" + properties.getMarket().getClose());
            log.info("SESSION_STARTED date={} previousState={}", todayIst(), previous);
        }
    }

    /** 15:10 (and restart catch-up): -> SESSION_CLOSING, trading window closed. Idempotent. */
    public void beginSessionClosing() {
        SessionState previous = lifecycle.getAndUpdate(s ->
                s == SessionState.SAFETY_EXIT_COMPLETED || s == SessionState.MARKET_CLOSED ? s : SessionState.SESSION_CLOSING);
        tradingStateService.closeTradingWindow();
        if (previous != SessionState.SESSION_CLOSING && previous != SessionState.SAFETY_EXIT_COMPLETED
                && previous != SessionState.MARKET_CLOSED) {
            auditService.record(AuditEventType.SESSION_CLOSING_STARTED, null, null,
                    "sessionDate=" + todayIst() + " previousState=" + previous
                            + " - new entries blocked, auto-closing system-managed positions");
            log.info("SESSION_CLOSING_STARTED date={} previousState={}", todayIst(), previous);
        }
    }

    /** All system-managed positions closed &amp; reconciled: SESSION_CLOSING -> SAFETY_EXIT_COMPLETED. */
    public void markSafetyExitComplete() {
        boolean moved = lifecycle.compareAndSet(SessionState.SESSION_CLOSING, SessionState.SAFETY_EXIT_COMPLETED);
        if (moved) {
            auditService.record(AuditEventType.SAFETY_EXIT_COMPLETED, null, null,
                    "sessionDate=" + todayIst() + " - all system-managed positions closed and reconciled");
            log.info("SAFETY_EXIT_COMPLETED date={}", todayIst());
        }
    }

    /** 15:30: -> MARKET_CLOSED, trading stays disabled. The JAR keeps running. */
    public void markOfficialMarketClosed() {
        SessionState previous = lifecycle.getAndSet(SessionState.MARKET_CLOSED);
        tradingStateService.closeTradingWindow();
        if (previous != SessionState.MARKET_CLOSED) {
            auditService.record(AuditEventType.OFFICIAL_MARKET_CLOSED, null, null,
                    "sessionDate=" + todayIst() + " officialClose=" + properties.getMarket().getClose()
                            + " previousState=" + previous);
            log.info("OFFICIAL_MARKET_CLOSED date={} previousState={}", todayIst(), previous);
        }
    }

    /**
     * Called once on application startup (and safe to call again): derives
     * the lifecycle state purely from the clock so a restart at any time of
     * day lands in the correct state.
     *
     * <ul>
     *   <li>non-trading day / before 09:25 -&gt; {@code PRE_MARKET}</li>
     *   <li>09:25-15:10 -&gt; {@code TRADING_ACTIVE} (window opened)</li>
     *   <li>15:10-15:30 -&gt; {@code SESSION_CLOSING} (caller then runs the close)</li>
     *   <li>15:30+ -&gt; {@code MARKET_CLOSED}</li>
     * </ul>
     *
     * Never re-opens trading once the cutoff has passed.
     */
    public SessionState recomputeStateOnStartup() {
        EntryWindow window = classifyEntryWindow();
        LocalTime now = nowIst().toLocalTime();
        boolean tradingDay = isTradingDay(todayIst());

        if (!tradingDay || window == EntryWindow.BEFORE_START) {
            lifecycle.set(SessionState.PRE_MARKET);
            tradingStateService.closeTradingWindow();
        } else if (window == EntryWindow.OPEN) {
            activateTradingSession();
        } else if (now.isBefore(officialClose())) {
            beginSessionClosing();
        } else {
            markOfficialMarketClosed();
        }
        log.info("SESSION_STATE_RECOMPUTED_ON_STARTUP state={} nowIst={} tradingDay={}",
                lifecycle.get(), nowIst(), tradingDay);
        return lifecycle.get();
    }

    // Visible for tests / diagnostics.
    void forceLifecycleState(SessionState state) {
        lifecycle.set(state);
    }
}
