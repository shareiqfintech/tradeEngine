package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.TradingMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The runtime switchboard: whether the 09:15-15:30 trading window is
 * currently open (set by {@code TradingWindowScheduler}), the kill switch,
 * and pause/resume - plus the (config-only, never runtime-toggled) trading
 * mode. {@code TradingEngineService} must check
 * {@link #isTradingAllowed()} before doing anything that could place an
 * order.
 */
@Slf4j
@Service
public class TradingStateService {

    private final AtomicBoolean tradingWindowOpen = new AtomicBoolean(false);
    private final AtomicBoolean killSwitchEnabled = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    private final TradingProperties properties;
    private final AuditService auditService;

    public TradingStateService(TradingProperties properties, AuditService auditService) {
        this.properties = properties;
        this.auditService = auditService;
    }

    public boolean isTradingAllowed() {
        return tradingWindowOpen.get() && !killSwitchEnabled.get() && !paused.get();
    }

    public boolean isTradingWindowOpen() {
        return tradingWindowOpen.get();
    }

    public void openTradingWindow() {
        tradingWindowOpen.set(true);
        log.info("TRADING_WINDOW_OPENED");
    }

    public void closeTradingWindow() {
        tradingWindowOpen.set(false);
        log.info("TRADING_WINDOW_CLOSED");
    }

    public boolean isKillSwitchEnabled() {
        return killSwitchEnabled.get();
    }

    public void enableKillSwitch() {
        killSwitchEnabled.set(true);
        auditService.record(AuditEventType.KILL_SWITCH_ENABLED, null, null, "Kill switch enabled - no new orders");
        log.warn("KILL_SWITCH_ENABLED");
    }

    public void disableKillSwitch() {
        killSwitchEnabled.set(false);
        auditService.record(AuditEventType.KILL_SWITCH_DISABLED, null, null, "Kill switch disabled");
        log.warn("KILL_SWITCH_DISABLED");
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void pause() {
        paused.set(true);
        auditService.record(AuditEventType.TRADING_PAUSED, null, null, "Trading paused");
        log.warn("TRADING_PAUSED");
    }

    public void resume() {
        paused.set(false);
        auditService.record(AuditEventType.TRADING_RESUMED, null, null, "Trading resumed");
        log.warn("TRADING_RESUMED");
    }

    public TradingMode getMode() {
        return properties.getMode();
    }
}
