package com.example.trading.service;

import com.example.trading.dto.Position;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OptionType;
import com.example.trading.exception.PositionNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Position-related business logic used by {@code TradingEngineService},
 * built on top of {@link GrowwPositionService} (which owns the actual
 * Groww API calls / wire-format mapping).
 *
 * <p>This is where the SELL-signal rule from the spec lives: a SELL never
 * opens a short (unless {@code trading.allow-short-selling=true}, not the
 * default) - it must find the existing open LONG position for the
 * underlying, and sell exactly that contract, in exactly that quantity.
 */
@Slf4j
@Service
public class PositionService {

    private final GrowwPositionService growwPositionService;
    private final AuditService auditService;

    public PositionService(GrowwPositionService growwPositionService, AuditService auditService) {
        this.growwPositionService = growwPositionService;
        this.auditService = auditService;
    }

    /**
     * Resolves the exact open LONG position a SELL signal for
     * {@code underlying} must close.
     *
     * @throws PositionNotFoundException (reason NO_LONG_POSITION) if none exists
     */
    public Position resolveLongPositionForSell(Long userId, String underlying, String signalId) {
        return growwPositionService.findLongPosition(userId, underlying, signalId)
                .orElseGet(() -> {
                    auditService.record(AuditEventType.NO_LONG_POSITION, signalId, null,
                            "underlying=" + underlying + " - no open long position to sell, allowShortSelling=false");
                    throw new PositionNotFoundException(
                            "NO_LONG_POSITION: no open long position found for underlying " + underlying);
                });
    }

    /** The exact quantity available to sell from an existing long position - never more than what is actually held. */
    public int availableQuantityToSell(Position longPosition) {
        return longPosition.getNetQuantity() == null ? 0 : Math.max(0, longPosition.getNetQuantity());
    }

    /**
     * Finds the user's active (long, net quantity &gt; 0) NIFTY option
     * position on a specific side (CE or PE) - used by the signal-driven
     * position-switching flow in {@code TradingEngineService} to locate the
     * opposite-side position that must be fully closed before a new
     * same-direction contract is bought. Returns empty if none is held -
     * that is not an error, just "nothing to switch out of".
     */
    public Optional<Position> findActivePosition(Long userId, String underlying, OptionType optionType, String signalId) {
        return growwPositionService.findActivePosition(userId, underlying, optionType, signalId);
    }
}
