package com.example.trading.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PUT /api/trading/fno/config/{underlying} body. {@code lotSize} is
 * optional - omit it (or send null) to use the resolved contract's own
 * current lot size as the default; if present, the backend validates it
 * against Groww's real instrument master before accepting it (see
 * OptionContractResolver#selectLotSize) - it is never trusted blindly.
 * {@code targetPoints} is optional too - null uses
 * {@code trading.exit.target.default-points}; a supplied value is validated
 * against the configured min/max and must be &gt; 0.
 */
@Data
public class FnoTradeConfigRequest {

    /** AUTO | CE | PE */
    @NotBlank
    private String optionType;

    /** ATM | ITM | OTM */
    @NotBlank
    private String strikeSelection;

    @Min(0)
    private int strikeOffset;

    /** NEAREST | NEXT */
    @NotBlank
    private String expirySelection;

    @Min(1)
    private int lots;

    /** Null = no explicit user selection - use the contract's current lot size. */
    private Integer lotSize;

    /**
     * Automatic profit-target size in points. Null = use the server default
     * ({@code trading.exit.target.default-points}). When present it must be
     * &gt; 0 and within {@code trading.exit.target.min-points/max-points} -
     * validated server-side, never trusted blindly.
     */
    private BigDecimal targetPoints;

    /** Null = leave the current per-underlying toggle unchanged (defaults to enabled). */
    private Boolean targetEnabled;
}
