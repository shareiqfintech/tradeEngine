package com.example.trading.dto;

import com.example.trading.enums.OptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A fully-resolved, order-ready F&O option contract. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionContract {
    private String tradingSymbol;
    private String underlying;
    private LocalDate expiry;
    private BigDecimal strike;
    private OptionType optionType;
    private int lotSize;
    private String exchange;
    private String segment;
    private boolean buyAllowed;
    private boolean sellAllowed;
}
