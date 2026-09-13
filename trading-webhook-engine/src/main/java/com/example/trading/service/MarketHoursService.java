package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * "Is the official NSE session running right now" check: Monday-Friday,
 * {@code trading.market.start} (09:25) to {@code trading.market.close}
 * (15:30) IST - the WHOLE official session, including the 15:10-15:30
 * safety-exit window, so order/position reconciliation keeps running there.
 *
 * <p>The 15:10 new-entry cutoff and the session lifecycle live in
 * {@link TradingSessionService}, not here. Deliberately does not model NSE
 * trading holidays - see {@code TradingSessionService.isTradingDay}.
 */
@Service
public class MarketHoursService {

    private final TradingProperties properties;
    private final Clock clock;

    public MarketHoursService(TradingProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isMarketOpenNow() {
        return isMarketOpenAt(clock.instant());
    }

    public boolean isMarketOpenAt(Instant instant) {
        ZoneId zone = properties.getZoneId();
        ZonedDateTime zoned = instant.atZone(zone);

        if (zoned.getDayOfWeek() == DayOfWeek.SATURDAY || zoned.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime time = zoned.toLocalTime();
        LocalTime start = properties.getMarket().startTime();
        LocalTime close = properties.getMarket().closeTime();

        return !time.isBefore(start) && !time.isAfter(close);
    }
}
