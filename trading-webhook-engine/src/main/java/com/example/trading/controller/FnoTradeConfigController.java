package com.example.trading.controller;

import com.example.trading.dto.FnoTradeConfigRequest;
import com.example.trading.dto.FnoTradeConfigResponse;
import com.example.trading.security.AuthenticatedUser;
import com.example.trading.service.FnoTradeConfigService;
import com.example.trading.service.InstrumentMasterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Operator-facing F&O trade configuration: which underlyings are
 * available (sourced live from Groww's instrument master, never a
 * hardcoded list), and per-underlying contract-resolution settings
 * including the lot-size override. The backend remains the sole authority
 * on whether a submitted lot size is actually valid - see
 * {@link com.example.trading.service.OptionContractResolver}.
 */
@RestController
@RequestMapping("/api/trading/fno")
public class FnoTradeConfigController {

    private final FnoTradeConfigService fnoTradeConfigService;
    private final InstrumentMasterService instrumentMasterService;

    public FnoTradeConfigController(FnoTradeConfigService fnoTradeConfigService,
                                     InstrumentMasterService instrumentMasterService) {
        this.fnoTradeConfigService = fnoTradeConfigService;
        this.instrumentMasterService = instrumentMasterService;
    }

    /** Distinct underlyings Groww's instrument master currently lists F&O options for. */
    @GetMapping("/underlyings")
    public ResponseEntity<List<String>> listUnderlyings() {
        return ResponseEntity.ok(instrumentMasterService.listAvailableUnderlyings());
    }

    @GetMapping("/config/{underlying}")
    public ResponseEntity<FnoTradeConfigResponse> getConfig(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @PathVariable String underlying) {
        return ResponseEntity.ok(fnoTradeConfigService.getConfig(user.id(), underlying));
    }

    @PutMapping("/config/{underlying}")
    public ResponseEntity<FnoTradeConfigResponse> updateConfig(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @PathVariable String underlying,
                                                                @Valid @RequestBody FnoTradeConfigRequest request) {
        return ResponseEntity.ok(fnoTradeConfigService.updateConfig(user.id(), underlying, request));
    }
}
