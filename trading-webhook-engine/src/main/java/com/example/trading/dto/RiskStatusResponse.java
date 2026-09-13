package com.example.trading.dto;

import com.example.trading.service.RiskManagementService.RiskUsageSnapshot;
import lombok.Builder;
import lombok.Data;

/**
 * GET /api/trading/risk. Limits mirror TradingProperties.Risk (real
 * server-side config); usage counters come straight from
 * {@link RiskManagementService#getUsageSnapshot()} - the exact same numbers
 * risk evaluation itself checks against.
 *
 * <p>{@code quantityUsedToday} is deliberately always {@code null}:
 * {@code max-quantity} is enforced PER ORDER (see
 * RiskManagementService#evaluateInternal), not as a cumulative daily total -
 * there is no real "quantity used today" counter to report without
 * inventing a meaning the backend doesn't actually enforce.
 */
@Data
@Builder
public class RiskStatusResponse {
    private int maxOrdersPerDay;
    private long ordersToday;
    private int maxOpenPositions;
    private long openPositions;
    private int maxQuantity;
    private Integer quantityUsedToday;
    private double maxDailyLoss;
    private double dailyLossSoFar;
    private int maxOrdersPerSignal;
    private boolean allowShortSelling;

    public static RiskStatusResponse from(RiskUsageSnapshot snapshot) {
        return RiskStatusResponse.builder()
                .maxOrdersPerDay(snapshot.maxOrdersPerDay())
                .ordersToday(snapshot.ordersToday())
                .maxOpenPositions(snapshot.maxOpenPositions())
                .openPositions(snapshot.openPositions())
                .maxQuantity(snapshot.maxQuantity())
                .quantityUsedToday(null)
                .maxDailyLoss(snapshot.maxDailyLoss())
                .dailyLossSoFar(snapshot.dailyLossSoFar())
                .maxOrdersPerSignal(snapshot.maxOrdersPerSignal())
                .allowShortSelling(snapshot.allowShortSelling())
                .build();
    }
}
