package com.example.trading.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one place that turns an ACTUAL filled entry price + Target Points into
 * a target price, for a LONG option position:
 *
 * <pre>targetPrice = actualFilledEntryPrice + targetPoints</pre>
 *
 * <p>All-{@link BigDecimal}, no floating point. The result is normalized to
 * the exchange tick size (default 0.05) - a no-op for typical values
 * (142+8=150.00, 142.25+8=150.25, 99.50+10=109.50) but keeps the target on
 * a real tradable price when Target Points or the fill carry odd decimals.
 * The TradingView signal price is NEVER an input here.
 */
@Component
public class TargetCalculator {

    private static final int PRICE_SCALE = 4;

    /**
     * @param entryPrice   the ACTUAL filled entry price from Groww (must be &gt; 0)
     * @param targetPoints the user's configured Target Points (must be &gt; 0)
     * @param tickSize     exchange tick size to normalize to; {@code null} or &le; 0 disables normalization
     */
    public BigDecimal targetPrice(BigDecimal entryPrice, BigDecimal targetPoints, BigDecimal tickSize) {
        if (entryPrice == null || entryPrice.signum() <= 0) {
            throw new IllegalArgumentException("entryPrice must be a positive actual fill price, was " + entryPrice);
        }
        if (targetPoints == null || targetPoints.signum() <= 0) {
            throw new IllegalArgumentException("targetPoints must be > 0, was " + targetPoints);
        }
        BigDecimal raw = entryPrice.add(targetPoints);
        if (tickSize == null || tickSize.signum() <= 0) {
            return raw.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal ticks = raw.divide(tickSize, 0, RoundingMode.HALF_UP);
        return ticks.multiply(tickSize).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
