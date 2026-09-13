package com.example.trading.controller;

import com.example.trading.dto.GrowwConnectionTestResponse;
import com.example.trading.dto.GrowwSettingsRequest;
import com.example.trading.dto.GrowwSettingsResponse;
import com.example.trading.security.AuthenticatedUser;
import com.example.trading.service.GrowwConnectionService;
import com.example.trading.service.GrowwSettingsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Per-user Groww API key / TOTP configuration. Every method takes the
 * caller's identity ONLY from {@code @AuthenticationPrincipal} (resolved by
 * Spring Security from the request's JWT) - never from the request body -
 * so ownership cannot be spoofed by anything the client sends.
 */
@RestController
@RequestMapping("/api/settings/groww")
public class GrowwSettingsController {

    private final GrowwSettingsService growwSettingsService;
    private final GrowwConnectionService growwConnectionService;

    public GrowwSettingsController(GrowwSettingsService growwSettingsService, GrowwConnectionService growwConnectionService) {
        this.growwSettingsService = growwSettingsService;
        this.growwConnectionService = growwConnectionService;
    }

    @GetMapping
    public GrowwSettingsResponse getSettings(@AuthenticationPrincipal AuthenticatedUser user) {
        return growwSettingsService.getSettings(user.id());
    }

    @PutMapping
    public GrowwSettingsResponse saveSettings(@AuthenticationPrincipal AuthenticatedUser user,
                                               @RequestBody GrowwSettingsRequest request) {
        return growwSettingsService.saveSettings(user.id(), request);
    }

    @PostMapping("/test-connection")
    public GrowwConnectionTestResponse testConnection(@AuthenticationPrincipal AuthenticatedUser user) {
        return growwConnectionService.testConnection(user.id());
    }
}
