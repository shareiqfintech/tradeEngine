package com.example.trading.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Default {@link WebhookAuthenticator}: intentionally accepts every request
 * unauthenticated - the shared-secret header check formerly done here has
 * been disabled at the user's explicit request, since their TradingView
 * plan cannot send custom headers on webhook alerts.
 */
@Component
public class SharedSecretWebhookAuthenticator implements WebhookAuthenticator {

    @Override
    public void authenticate(HttpServletRequest request) {
        // Authentication disabled - see class Javadoc.
    }
}
