package com.example.trading.scheduler;

import com.example.trading.groww.GrowwAuthenticationService;
import com.example.trading.service.GrowwUserResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Authenticates with Groww at 08:00 IST, Monday-Friday - well before the
 * 09:15 trading window opens, so any {@code AUTH_REQUIRED} state is
 * discovered (and can be alerted on / retried) with time to spare.
 *
 * <p>If the 08:00 attempt fails, a small number of spaced-out retries run
 * between 08:00 and 09:15 (every 5 minutes, capped at
 * {@link #MAX_RETRIES_AFTER_MORNING} extra attempts) - this is deliberately
 * NOT a tight retry loop hammering Groww's endpoint. If every retry fails,
 * the state stays {@code AUTH_REQUIRED}/{@code AUTH_FAILED} and
 * {@code TradingWindowScheduler} will refuse to open the trading window at
 * 09:15, per the "no trading until authentication succeeds" requirement.
 */
@Slf4j
@Component
public class GrowwAuthenticationScheduler {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_RETRIES_AFTER_MORNING = 3;
    private static final LocalTime RETRY_CUTOFF = LocalTime.of(9, 15);

    private final GrowwAuthenticationService growwAuthenticationService;
    private final GrowwUserResolver growwUserResolver;
    private final AtomicInteger retryCount = new AtomicInteger(0);

    public GrowwAuthenticationScheduler(GrowwAuthenticationService growwAuthenticationService, GrowwUserResolver growwUserResolver) {
        this.growwAuthenticationService = growwAuthenticationService;
        this.growwUserResolver = growwUserResolver;
    }

    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Kolkata")
    public void morningAuthenticate() {
        retryCount.set(0);
        log.info("GROWW_MORNING_AUTH_STARTED");
        attempt();
    }

    /** Bounded, spaced-out retry - only while still before the trading window and only a few times. */
    @Scheduled(cron = "0 0/5 8-9 * * MON-FRI", zone = "Asia/Kolkata")
    public void retryIfStillUnauthenticated() {
        List<Long> connectedUserIds = growwUserResolver.findAllConnectedUserIds();
        boolean anyUnauthenticated = connectedUserIds.stream().anyMatch(id -> !growwAuthenticationService.isAuthenticated(id));
        if (connectedUserIds.isEmpty() || !anyUnauthenticated) {
            return;
        }
        if (LocalTime.now(IST).isAfter(RETRY_CUTOFF)) {
            return;
        }
        if (retryCount.get() >= MAX_RETRIES_AFTER_MORNING) {
            log.warn("GROWW_AUTH_RETRY_BUDGET_EXHAUSTED retries={}", retryCount.get());
            return;
        }
        retryCount.incrementAndGet();
        log.info("GROWW_AUTH_RETRY attempt={}", retryCount.get());
        attempt();
    }

    /** Authenticates every connected user independently - one user's failure never blocks the others. */
    private void attempt() {
        List<Long> connectedUserIds = growwUserResolver.findAllConnectedUserIds();
        if (connectedUserIds.isEmpty()) {
            log.warn("GROWW_MORNING_AUTH_SKIPPED reason=NO_CONNECTED_USERS");
            return;
        }
        for (Long userId : connectedUserIds) {
            try {
                boolean ok = growwAuthenticationService.authenticate(userId);
                if (ok) {
                    log.info("GROWW_MORNING_AUTH_SUCCEEDED userId={}", userId);
                } else {
                    log.warn("GROWW_MORNING_AUTH_FAILED userId={} state={}", userId, growwAuthenticationService.getState(userId));
                }
            } catch (Exception ex) {
                log.error("GROWW_MORNING_AUTH_ERROR userId={}", userId, ex);
            }
        }
    }
}
