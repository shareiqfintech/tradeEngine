package com.example.trading.dto;

import lombok.Builder;
import lombok.Data;

/** POST /api/auth/login response - the session JWT plus the basic profile the frontend needs, nothing else. */
@Data
@Builder
public class AuthResponse {
    private Long id;
    private String name;
    private String email;
    private String token;
}
