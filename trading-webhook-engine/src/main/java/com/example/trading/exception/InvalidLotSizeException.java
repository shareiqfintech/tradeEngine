package com.example.trading.exception;

import lombok.Getter;

import java.util.List;

/**
 * Thrown when a user-selected lot size does not match any lot size Groww's
 * instrument master actually reports for the resolved contract
 * (underlying + expiry + strike + option type). The backend is the sole
 * authority on this - a lot size is never accepted just because a caller
 * (frontend or otherwise) supplied it.
 */
@Getter
public class InvalidLotSizeException extends RuntimeException {

    private final int requestedLotSize;
    private final List<Integer> validLotSizes;

    public InvalidLotSizeException(int requestedLotSize, List<Integer> validLotSizes) {
        super("Lot size " + requestedLotSize + " is not valid for this contract. Valid lot size(s): " + validLotSizes);
        this.requestedLotSize = requestedLotSize;
        this.validLotSizes = validLotSizes;
    }
}
