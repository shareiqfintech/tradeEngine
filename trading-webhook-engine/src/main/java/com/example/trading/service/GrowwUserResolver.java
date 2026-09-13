package com.example.trading.service;

import com.example.trading.entity.GrowwConfigurationEntity;
import com.example.trading.repository.GrowwConfigurationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves WHICH application users' Groww credentials the webhook-triggered
 * automated trading engine (TradingEngineService, the Groww auth/trading
 * schedulers) should use. Unlike a browser request, a TradingView webhook
 * call carries no application-user session at all - it is authenticated by
 * a shared secret, not a login - so there is no {@code SecurityContext} to
 * resolve a user from there.
 *
 * <p>Crucially, a TradingView alert does not belong to any one application
 * user either: it is one common trading signal (e.g. "BUY NIFTY") that must
 * be executed independently for EVERY user who currently has a verified
 * (connected=true) Groww configuration - the same "connected" flag the
 * user's own Test Connection action sets - never routed to just one of
 * them, and never picked by "most recently connected".
 */
@Service
public class GrowwUserResolver {

    private final GrowwConfigurationRepository repository;

    public GrowwUserResolver(GrowwConfigurationRepository repository) {
        this.repository = repository;
    }

    /** Every application user currently connected to Groww - empty if nobody is, and never just one of several. */
    public List<Long> findAllConnectedUserIds() {
        return repository.findByConnectedTrueOrderByLastConnectedAtDesc().stream()
                .map(GrowwConfigurationEntity::getUserId)
                .toList();
    }
}
