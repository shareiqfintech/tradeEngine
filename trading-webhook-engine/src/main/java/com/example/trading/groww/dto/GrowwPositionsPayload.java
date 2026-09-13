package com.example.trading.groww.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The real {@code payload} shape of GET /v1/positions/user and
 * GET /v1/positions/trading-symbol: {@code {"positions": [...]}} - an
 * object wrapping the array, NOT a bare JSON array. Confirmed against
 * Groww's documented example response.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrowwPositionsPayload {
    private List<GrowwPositionDto> positions;
}
