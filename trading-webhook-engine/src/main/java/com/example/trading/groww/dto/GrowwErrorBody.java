package com.example.trading.groww.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Groww's error object. Accepts BOTH field-naming variants seen in
 * practice: the published-docs shape ({@code code}/{@code message}) and
 * the shape actually observed live from {@code POST /v1/token/api/access}
 * ({@code errorCode}/{@code errorMessage}) - see GrowwApiClient#parseErrorBody.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrowwErrorBody {

    @JsonAlias("errorCode")
    private String code;

    @JsonAlias("errorMessage")
    private String message;

    private Object metadata;
}
