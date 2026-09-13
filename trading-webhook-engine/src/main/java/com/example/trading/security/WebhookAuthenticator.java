package com.example.trading.security;

import com.example.trading.exception.InvalidWebhookSecretException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Verifies that an inbound webhook request is genuinely from TradingView.
 *
 * <p>Phase 1 ships a single implementation, {@link SharedSecretWebhookAuthenticator},
 * which checks the {@code X-TradingView-Secret} header against a
 * pre-shared value. This interface exists so a future implementation
 * (for example a {@code CertificateWebhookAuthenticator} performing
 * mutual-TLS / client-certificate verification, per TradingView's HTTPS
 * webhook authentication support) can be introduced later and selected via
 * {@code trading.webhook.auth-mode} without touching the controller or any
 * other caller.
 */
public interface WebhookAuthenticator {

    /**
     * @throws InvalidWebhookSecretException if the request cannot be authenticated
     */
    void authenticate(HttpServletRequest request);
}
