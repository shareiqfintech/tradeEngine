package com.example.trading.groww.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One row of {@code GET /v1/positions/user} / {@code GET /v1/positions/trading-symbol}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrowwPositionDto {

    @JsonProperty("trading_symbol")
    private String tradingSymbol;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("symbol_isin")
    private String symbolIsin;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("product")
    private String product;

    @JsonProperty("credit_quantity")
    private Integer creditQuantity;

    @JsonProperty("credit_price")
    private BigDecimal creditPrice;

    @JsonProperty("debit_quantity")
    private Integer debitQuantity;

    @JsonProperty("debit_price")
    private BigDecimal debitPrice;

    @JsonProperty("carry_forward_credit_quantity")
    private Integer carryForwardCreditQuantity;

    @JsonProperty("carry_forward_credit_price")
    private BigDecimal carryForwardCreditPrice;

    @JsonProperty("carry_forward_debit_quantity")
    private Integer carryForwardDebitQuantity;

    @JsonProperty("carry_forward_debit_price")
    private BigDecimal carryForwardDebitPrice;

    @JsonProperty("net_carry_forward_quantity")
    private Integer netCarryForwardQuantity;

    @JsonProperty("net_price")
    private BigDecimal netPrice;

    @JsonProperty("net_carry_forward_price")
    private BigDecimal netCarryForwardPrice;

    @JsonProperty("realised_pnl")
    private BigDecimal realisedPnl;
}
