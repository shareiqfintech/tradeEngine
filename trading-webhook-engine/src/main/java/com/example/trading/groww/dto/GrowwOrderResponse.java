package com.example.trading.groww.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Payload shared by order create / status / status-by-reference / detail responses. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrowwOrderResponse {

    @JsonProperty("groww_order_id")
    private String growwOrderId;

    @JsonProperty("order_reference_id")
    private String orderReferenceId;

    @JsonProperty("trading_symbol")
    private String tradingSymbol;

    @JsonProperty("order_status")
    private String orderStatus;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("filled_quantity")
    private Integer filledQuantity;

    @JsonProperty("average_fill_price")
    private BigDecimal averageFillPrice;

    @JsonProperty("remark")
    private String remark;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("segment")
    private String segment;
}
