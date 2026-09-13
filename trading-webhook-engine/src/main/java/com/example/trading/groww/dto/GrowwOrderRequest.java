package com.example.trading.groww.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Body of {@code POST /v1/order/create}. Field names/values follow the
 * documented Groww Trade API contract exactly - no undocumented parameters
 * are invented here.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrowwOrderRequest {

    @JsonProperty("trading_symbol")
    private String tradingSymbol;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("trigger_price")
    private BigDecimal triggerPrice;

    @JsonProperty("validity")
    private String validity;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("segment")
    private String segment;

    @JsonProperty("product")
    private String product;

    @JsonProperty("order_type")
    private String orderType;

    @JsonProperty("transaction_type")
    private String transactionType;

    @JsonProperty("order_reference_id")
    private String orderReferenceId;
}
