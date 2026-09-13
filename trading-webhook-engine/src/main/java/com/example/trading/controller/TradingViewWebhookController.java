package com.example.trading.controller;

import com.example.trading.dto.TradingViewSignal;
import com.example.trading.dto.WebhookResponse;
import com.example.trading.entity.TradingSignalEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.SignalStatus;
import com.example.trading.exception.TradingSessionClosedException;
import com.example.trading.redis.SignalDeduplicationService;
import com.example.trading.repository.TradingSignalRepository;
import com.example.trading.service.AuditService;
import com.example.trading.service.SignalValidationService;
import com.example.trading.service.TradingEngineService;
import com.example.trading.service.TradingSessionService;
import jakarta.validation.Valid;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives TradingView alert webhooks.
 *
 * <p>Per the project's build spec, this controller does exactly the intake
 * half of the pipeline, synchronously and fast:
 * authenticate (filter) -&gt; validate -&gt; duplicate check -&gt; save -&gt; 202.
 * Everything else (market hours, trading state, Groww auth, position
 * checks, contract resolution, risk, order placement, order status,
 * audit) happens in {@link TradingEngineService}, off this request thread,
 * so TradingView always gets its 202 in well under a second - including
 * when the signal turns out to be a duplicate: TradingView is never told
 * "no", it is simply not dispatched to the engine a second time.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
public class TradingViewWebhookController {

    private final SignalValidationService signalValidationService;
    private final SignalDeduplicationService signalDeduplicationService;
    private final TradingSignalRepository signalRepository;
    private final TradingEngineService tradingEngineService;
    private final TradingSessionService tradingSessionService;
    private final AuditService auditService;

    public TradingViewWebhookController(SignalValidationService signalValidationService,
                                         SignalDeduplicationService signalDeduplicationService,
                                         TradingSignalRepository signalRepository,
                                         TradingEngineService tradingEngineService,
                                         TradingSessionService tradingSessionService,
                                         AuditService auditService) {
        this.signalValidationService = signalValidationService;
        this.signalDeduplicationService = signalDeduplicationService;
        this.signalRepository = signalRepository;
        this.tradingEngineService = tradingEngineService;
        this.tradingSessionService = tradingSessionService;
        this.auditService = auditService;
    }

    @PostMapping(value = "/tradingview", consumes = "application/json")
    public ResponseEntity<WebhookResponse> receiveSignal(@Valid @RequestBody TradingViewSignal signal) {
        log.info("SIGNAL_RECEIVED signalId={} action={} underlying={} exchange={} timeframe={}",
                signal.getSignalId(), signal.getAction(), signal.getUnderlying(),
                signal.getExchange(), signal.getTimeframe());
        auditService.record(AuditEventType.SIGNAL_RECEIVED, signal.getSignalId(), null,
                "action=" + signal.getAction() + " underlying=" + signal.getUnderlying());

        // 1. Validate signal (structural/business rules beyond bean validation)
        signalValidationService.validateStructure(signal);

        // 2. Duplicate check (Redis: trading:signal:{signalId}, TTL 24h)
        boolean firstSeen = signalDeduplicationService.markIfFirstSeen(signal.getSignalId());

        if (!firstSeen) {
            // A row for this signalId already exists from the first time it was
            // seen (trading_signal.signal_id is UNIQUE) - never insert again,
            // just record that a duplicate arrived and stop here.
            auditService.record(AuditEventType.DUPLICATE_SIGNAL, signal.getSignalId(), null,
                    "Signal already processed within the last 24h");
        } else {
            // 3. Save signal
            TradingSignalEntity entity = TradingSignalEntity.received(
                    signal.getSignalId(), signal.getAction(), signal.getUnderlying(),
                    signal.getExchange(), signal.getTimeframe(), signal.getPrice(), signal.getTimestamp());

            // 4. Session-window fast path. A signal that arrives before 09:25
            // or at/after 15:10 IST is persisted and marked rejected here -
            // it is NEVER dispatched to the engine and NEVER reaches Groww.
            // (The engine still runs its own independent check for the
            // async-queue race: accepted before 15:10, executed after.)
            Optional<String> outsideWindow = tradingSessionService.classifyNewEntry();
            if (outsideWindow.isPresent()) {
                String reasonCode = outsideWindow.get();
                entity.setStatus(SignalStatus.REJECTED);
                entity.setRejectionReason(reasonCode + ": new entry outside the 09:25-15:10 IST trading window");
                signalRepository.save(entity);
                auditService.record(sessionRejectAudit(reasonCode), signal.getSignalId(), null,
                        "action=" + signal.getAction() + " underlying=" + signal.getUnderlying());
                log.info("SIGNAL_REJECTED_AT_INTAKE signalId={} reasonCode={}", signal.getSignalId(), reasonCode);
            } else {
                signalRepository.save(entity);
                auditService.record(AuditEventType.SIGNAL_SAVED, signal.getSignalId(), null, "status=RECEIVED");

                // 5. Async processing -> TradingEngineService (never on this request thread)
                tradingEngineService.processSignalAsync(signal.getSignalId());
            }
        }

        // 6. Return HTTP 202 - always: duplicate, outside-window, or accepted.
        //    TradingView is never told "no"; the webhook endpoint stays up 24/7.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(WebhookResponse.accepted(signal.getSignalId()));
    }

    private static AuditEventType sessionRejectAudit(String reasonCode) {
        return TradingSessionClosedException.SESSION_NOT_STARTED.equals(reasonCode)
                ? AuditEventType.NEW_ENTRY_REJECTED_SESSION_NOT_STARTED
                : AuditEventType.NEW_ENTRY_REJECTED_TRADING_CUTOFF;
    }
}
