package com.example.trading.service;

import com.example.trading.exception.ContractNotFoundException;
import com.example.trading.groww.GrowwApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Fetches the current spot price of an underlying (e.g. NIFTY index) from
 * Groww's live-data API, for contract resolution/preview when there is no
 * TradingView signal price to use - the F&O trade configuration UI needs to
 * show "Selected Contract" and a valid lot size before any signal has
 * arrived. Never a fabricated/assumed price: if Groww can't be reached,
 * callers get a clear failure rather than a guessed number.
 */
@Slf4j
@Service
public class LiveQuoteService {

    private static final String INDEX_SEGMENT = "CASH";
    private static final String NSE = "NSE";

    private final GrowwApiClient growwApiClient;

    public LiveQuoteService(GrowwApiClient growwApiClient) {
        this.growwApiClient = growwApiClient;
    }

    /**
     * Current spot/index price for {@code underlying} (e.g. "NIFTY"),
     * sourced from GET /v1/live-data/ltp.
     *
     * @throws ContractNotFoundException if Groww has no live price for this
     *                                    underlying (e.g. it isn't a real
     *                                    NSE symbol) - mapped to a clean
     *                                    422 by GlobalExceptionHandler,
     *                                    rather than surfacing as a raw 500.
     */
    public BigDecimal getSpotPrice(Long userId, String underlying) {
        BigDecimal ltp = growwApiClient.getLtp(userId, NSE, INDEX_SEGMENT, underlying.trim().toUpperCase());
        if (ltp == null) {
            throw new ContractNotFoundException("Groww returned no live price for underlying " + underlying);
        }
        return ltp;
    }
}
