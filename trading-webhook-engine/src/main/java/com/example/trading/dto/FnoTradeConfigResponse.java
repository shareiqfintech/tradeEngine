package com.example.trading.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response for both GET and PUT /api/trading/fno/config/{underlying}.
 * {@code lotSize} is always the EFFECTIVE value actually used (the user's
 * selection if one was saved and is still valid, otherwise the resolved
 * contract's own current lot size) - {@code lotSizeSource} says which.
 * {@code quantity} is {@code lots * lotSize}, computed server-side; the
 * frontend must never compute this value itself as authoritative.
 */
@Data
@Builder
public class FnoTradeConfigResponse {
    private String underlying;
    private String optionType;
    private String strikeSelection;
    private int strikeOffset;
    private String expirySelection;
    private int lots;
    private int lotSize;
    private LotSizeSource lotSizeSource;
    private List<Integer> validLotSizes;
    private int quantity;
    private OptionContract resolvedContract;

    /**
     * The EFFECTIVE Target Points (the user's saved value, or the server
     * default). {@code targetPrice} is deliberately NOT here - it is
     * "Calculated after entry" from the real Groww fill, never previewed
     * from the signal/spot price.
     */
    private BigDecimal targetPoints;
    private boolean targetEnabled;
    private BigDecimal targetPointsMin;
    private BigDecimal targetPointsMax;

    public enum LotSizeSource {
        USER,
        DEFAULT
    }
}
