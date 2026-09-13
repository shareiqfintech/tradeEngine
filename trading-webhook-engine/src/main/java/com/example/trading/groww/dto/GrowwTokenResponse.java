package com.example.trading.groww.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payload of a successful {@code POST /v1/token/api/access} response. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrowwTokenResponse {
    private String token;
    private String tokenRefId;
    private String sessionName;
    private String expiry;
    private Boolean isActive;
}
