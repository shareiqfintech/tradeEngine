package com.example.trading.config;

import com.example.trading.enums.TradingMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Binds the entire {@code trading.*} configuration tree in one place so
 * every service depends on a single, typed, immutable-at-request-time
 * settings object instead of scattered {@code @Value} injections.
 */
@Data
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    /** PAPER (default, safe) or LIVE. Never flipped at runtime - only via config/restart. */
    private TradingMode mode = TradingMode.PAPER;

    private String timezone = "Asia/Kolkata";

    private boolean allowShortSelling = false;

    private final Market market = new Market();
    private final Session session = new Session();
    private final Exit exit = new Exit();
    private final Webhook webhook = new Webhook();
    private final Groww groww = new Groww();
    private final Quantity quantity = new Quantity();
    private final Option option = new Option();
    private final Risk risk = new Risk();
    private final Order order = new Order();

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }

    /**
     * Three distinct times, all Asia/Kolkata:
     * <ul>
     *   <li>{@code start} (09:25) - strategy session opens, new entries allowed</li>
     *   <li>{@code tradingCutoff} (15:10) - SAFETY EXIT: stop new entries, auto-close
     *       system-managed positions. NOT the market close.</li>
     *   <li>{@code close} (15:30) - official NSE close, everything stays disabled</li>
     * </ul>
     */
    @Data
    public static class Market {
        private String start = "09:25";
        private String tradingCutoff = "15:10";
        private String close = "15:30";

        public LocalTime startTime() {
            return LocalTime.parse(start, HH_MM);
        }

        public LocalTime tradingCutoffTime() {
            return LocalTime.parse(tradingCutoff, HH_MM);
        }

        public LocalTime closeTime() {
            return LocalTime.parse(close, HH_MM);
        }
    }

    /** The 15:10 safety-exit / automatic position-close behaviour. */
    @Data
    public static class Session {
        private boolean enabled = true;
        /** When true, the 15:10 scheduler closes every system-managed open position. */
        private boolean autoClosePositions = true;
        /** Kept for config clarity / parity with market.trading-cutoff (the effective cutoff is {@code market.tradingCutoff}). */
        private String safetyExitTime = "15:10";
    }

    /** Automatic profit-target exit (TARGET-ONLY, no stop-loss). See PositionTargetService / TargetMonitorScheduler. */
    @Data
    public static class Exit {
        private final Target target = new Target();

        @Data
        public static class Target {
            /** Master switch. When false, no target monitoring or auto-exit happens. */
            private boolean enabled = true;
            /** Used when a user has no saved Target Points for the underlying. */
            private BigDecimal defaultPoints = new BigDecimal("8");
            /** Minimum accepted Target Points (must be &gt; 0). */
            private BigDecimal minPoints = new BigDecimal("0.05");
            /** Maximum accepted Target Points. */
            private BigDecimal maxPoints = new BigDecimal("2000");
            /** How often the single target monitor polls the live option LTP. */
            private Duration pollInterval = Duration.ofSeconds(3);
            /** Exchange tick size the calculated target price is normalized to (0 disables normalization). */
            private BigDecimal tickSize = new BigDecimal("0.05");
        }
    }

    @Data
    public static class Webhook {
        private String secret;
        private String authMode = "SHARED_SECRET";
    }

    /**
     * Non-secret Groww API endpoint configuration only - the same for every
     * user. Each user's own API key/TOTP secret/access token is USER DATA,
     * never application configuration: it lives in {@code groww_configuration}
     * (see {@link com.example.trading.entity.GrowwConfigurationEntity}),
     * encrypted at rest, resolved per authenticated user at call time (see
     * {@link com.example.trading.groww.GrowwAuthenticationService}) - never here.
     */
    @Data
    public static class Groww {
        private String baseUrl = "https://api.groww.in";
        private String instrumentCsvUrl = "https://growwapi-assets.groww.in/instruments/instrument.csv";
        private String apiVersion = "1.0";
    }

    @Data
    public static class Quantity {
        /** Number of lots per signal. Actual quantity = lotSize (from instrument master) * lots. */
        private int lots = 1;
    }

    @Data
    public static class Option {
        /** AUTO | CE | PE. AUTO resolves CE for BUY signals (see OptionContractResolver). */
        private String optionType = "AUTO";
        /** ATM | ITM1 | ITM2 | OTM1 | OTM2 */
        private String strikeSelection = "ATM";
        private int strikeOffset = 0;
        /** NEAREST | NEXT */
        private String expirySelection = "NEAREST";
    }

    @Data
    public static class Risk {
        private int maxOrdersPerDay = 10;
        private int maxOpenPositions = 3;
        private int maxQuantity = 1000;
        private double maxDailyLoss = 5000;
        private int maxOrdersPerSignal = 1;
    }

    @Data
    public static class Order {
        private String product = "NRML";
        private String orderType = "MARKET";
        private String validity = "DAY";
    }
}
