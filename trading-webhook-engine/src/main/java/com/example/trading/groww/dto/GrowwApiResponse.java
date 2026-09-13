package com.example.trading.groww.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The generic response envelope Groww's Trade API uses. The published docs
 * describe {@code {"status": "SUCCESS"|"FAILURE", "payload": {...}, "error": {...}}},
 * but a live call to {@code POST /v1/token/api/access} was actually
 * observed returning {@code {"header": ..., "response": {...}, "error": {...}}}
 * with NO {@code status} field at all on failure. Rather than trust one
 * shape, this accepts both: {@code response} is aliased onto the same
 * field as {@code payload}, and success/failure is determined by whether
 * {@code error} is present - not by a {@code status} string that isn't
 * always there.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrowwApiResponse<T> {

    private String status;

    @JsonAlias("response")
    private T payload;

    private GrowwErrorBody error;

    public boolean isSuccess() {
        if (error != null) {
            return false;
        }
        if (status != null) {
            return "SUCCESS".equalsIgnoreCase(status);
        }
        return true;
    }
}
