package com.example.trading.dto;

import lombok.Builder;
import lombok.Data;

/** GET /api/auth/me response. */
@Data
@Builder
public class CurrentUserResponse {
    private Long id;
    private String name;
    private String email;
}
