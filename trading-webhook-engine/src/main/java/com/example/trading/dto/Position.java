package com.example.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Clean internal representation of a broker position, decoupled from
 * {@code GrowwPositionDto}'s raw wire field names. {@code netQuantity} is
 * the number that actually matters for trading decisions: positive means a
 * net long position, negative a net short position, zero means flat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {
    private String tradingSymbol;
    private String exchange;
    private String segment;
    private Integer quantity;
    private BigDecimal averagePrice;
    private String product;
    private Integer netQuantity;

    public boolean isLong() {
        return netQuantity != null && netQuantity > 0;
    }

    public boolean isShort() {
        return netQuantity != null && netQuantity < 0;
    }
}
