package com.example.trading.service;

import com.example.trading.dto.TradingViewSignal;
import com.example.trading.exception.InvalidSignalException;
import com.example.trading.exception.MarketClosedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Business-rule validation of an inbound signal, on top of the bean
 * validation ({@code @NotBlank}, {@code @DecimalMin}, ...) already enforced
 * by {@code @Valid} on the controller. This is where rules that need actual
 * logic (not just annotations) live: supported exchange, sane price, and
 * (later in the pipeline, not at intake) market hours.
 */
@Service
public class SignalValidationService {

    private static final Set<String> SUPPORTED_EXCHANGES = Set.of("NSE");
    private static final BigDecimal MAX_SANE_PRICE = new BigDecimal("1000000");

    private final MarketHoursService marketHoursService;

    public SignalValidationService(MarketHoursService marketHoursService) {
        this.marketHoursService = marketHoursService;
    }

    /** Structural/business validation performed synchronously at webhook intake. */
    public void validateStructure(TradingViewSignal signal) {
        if (signal.getAction() == null) {
            throw new InvalidSignalException("action must be BUY or SELL");
        }
        if (signal.getUnderlying() == null || signal.getUnderlying().isBlank()) {
            throw new InvalidSignalException("underlying must not be blank");
        }
        if (signal.getExchange() == null || !SUPPORTED_EXCHANGES.contains(signal.getExchange().toUpperCase())) {
            throw new InvalidSignalException("Unsupported exchange: " + signal.getExchange()
                    + " (supported: " + SUPPORTED_EXCHANGES + ")");
        }
        if (signal.getPrice() == null || signal.getPrice().signum() <= 0) {
            throw new InvalidSignalException("price must be greater than zero");
        }
        if (signal.getPrice().compareTo(MAX_SANE_PRICE) > 0) {
            throw new InvalidSignalException("price " + signal.getPrice() + " exceeds sanity bound " + MAX_SANE_PRICE);
        }
        if (signal.getTimestamp() == null) {
            throw new InvalidSignalException("timestamp is required");
        }
    }

    /** Market-hours gate, run inside the async engine (not at intake, so alerts fired just before/after the bell are still recorded). */
    public void validateMarketHours(TradingViewSignal signal) {
        if (!marketHoursService.isMarketOpenNow()) {
            throw new MarketClosedException("Market is closed (outside 09:15-15:30 IST, Mon-Fri)");
        }
    }
}
