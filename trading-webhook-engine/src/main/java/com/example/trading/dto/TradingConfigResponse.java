package com.example.trading.dto;

import com.example.trading.config.TradingProperties;
import com.example.trading.enums.TradingMode;
import lombok.Builder;
import lombok.Data;

/**
 * GET /api/trading/config. Mirrors {@link TradingProperties} exactly, minus
 * the {@code groww} block - the frontend must never receive the Groww API
 * key or TOTP secret over HTTP.
 */
@Data
@Builder
public class TradingConfigResponse {
    private TradingMode mode;
    private String timezone;
    private boolean allowShortSelling;
    private Market market;
    private Quantity quantity;
    private Option option;
    private Risk risk;
    private Order order;

    @Data
    @Builder
    public static class Market {
        private String start;
        /** 15:10 IST safety-exit / new-entry cutoff (NOT the market close). */
        private String tradingCutoff;
        /** 15:30 IST official NSE close. */
        private String close;
    }

    @Data
    @Builder
    public static class Quantity {
        private int lots;
    }

    @Data
    @Builder
    public static class Option {
        private String optionType;
        private String strikeSelection;
        private int strikeOffset;
        private String expirySelection;
    }

    @Data
    @Builder
    public static class Risk {
        private int maxOrdersPerDay;
        private int maxOpenPositions;
        private int maxQuantity;
        private double maxDailyLoss;
        private int maxOrdersPerSignal;
    }

    @Data
    @Builder
    public static class Order {
        private String product;
        private String orderType;
        private String validity;
    }

    public static TradingConfigResponse from(TradingProperties properties) {
        return TradingConfigResponse.builder()
                .mode(properties.getMode())
                .timezone(properties.getTimezone())
                .allowShortSelling(properties.isAllowShortSelling())
                .market(Market.builder()
                        .start(properties.getMarket().getStart())
                        .tradingCutoff(properties.getMarket().getTradingCutoff())
                        .close(properties.getMarket().getClose())
                        .build())
                .quantity(Quantity.builder()
                        .lots(properties.getQuantity().getLots())
                        .build())
                .option(Option.builder()
                        .optionType(properties.getOption().getOptionType())
                        .strikeSelection(properties.getOption().getStrikeSelection())
                        .strikeOffset(properties.getOption().getStrikeOffset())
                        .expirySelection(properties.getOption().getExpirySelection())
                        .build())
                .risk(Risk.builder()
                        .maxOrdersPerDay(properties.getRisk().getMaxOrdersPerDay())
                        .maxOpenPositions(properties.getRisk().getMaxOpenPositions())
                        .maxQuantity(properties.getRisk().getMaxQuantity())
                        .maxDailyLoss(properties.getRisk().getMaxDailyLoss())
                        .maxOrdersPerSignal(properties.getRisk().getMaxOrdersPerSignal())
                        .build())
                .order(Order.builder()
                        .product(properties.getOrder().getProduct())
                        .orderType(properties.getOrder().getOrderType())
                        .validity(properties.getOrder().getValidity())
                        .build())
                .build();
    }
}
