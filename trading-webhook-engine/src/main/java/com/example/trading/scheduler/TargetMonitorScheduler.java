package com.example.trading.scheduler;

import com.example.trading.config.TradingProperties;
import com.example.trading.service.PositionTargetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The ONE automatic-profit-target poller for the whole application - never
 * one thread/scheduler per position. Every {@code trading.exit.target.poll-
 * interval} it:
 * <ol>
 *   <li>promotes {@code PENDING_ENTRY} target rows whose fill price is now known;</li>
 *   <li>fetches the live LTP for every distinct monitored option (batched /
 *       de-duplicated per user), and submits exactly one SELL for any
 *       position whose {@code LTP >= targetPrice}.</li>
 * </ol>
 * On startup it reconciles any exit that was in flight when the JVM stopped;
 * monitoring itself resumes from the persisted {@code position_target} rows.
 */
@Slf4j
@Component
public class TargetMonitorScheduler {

    private final PositionTargetService positionTargetService;
    private final TradingProperties properties;

    public TargetMonitorScheduler(PositionTargetService positionTargetService, TradingProperties properties) {
        this.positionTargetService = positionTargetService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            positionTargetService.recoverOnStartup();
        } catch (Exception ex) {
            log.error("TARGET_MONITOR_STARTUP_RECOVERY_FAILED", ex);
        }
    }

    @Scheduled(fixedRateString = "${trading.exit.target.poll-interval:PT3S}")
    public void tick() {
        if (!properties.getExit().getTarget().isEnabled()) {
            return;
        }
        try {
            positionTargetService.resolvePendingEntries();
            positionTargetService.runMonitorTick();
        } catch (Exception ex) {
            log.error("TARGET_MONITOR_TICK_FAILED", ex);
        }
    }
}
