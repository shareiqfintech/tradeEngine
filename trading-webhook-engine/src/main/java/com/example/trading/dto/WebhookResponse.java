package com.example.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fast acknowledgement returned to TradingView. The webhook must respond in
 * well under a second - actual order processing happens asynchronously and is
 * never reflected in this response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {

    private String status;
    private String signalId;

    public static WebhookResponse accepted(String signalId) {
        return new WebhookResponse("ACCEPTED", signalId);
    }
}
