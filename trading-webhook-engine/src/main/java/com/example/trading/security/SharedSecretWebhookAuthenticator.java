package com.example.trading.security;

import com.example.trading.exception.InvalidWebhookSecretException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Default {@link WebhookAuthenticator}: validates the shared-secret header
 * TradingView is configured to send on every alert.
 *
 * <p>The secret is compared using {@link MessageDigest#isEqual(byte[], byte[])}
 * so the comparison time does not leak information about how many leading
 * characters matched. Neither the configured secret nor the value the caller
 * sent is ever logged or included in an exception message.
 */
@Component
public class SharedSecretWebhookAuthenticator implements WebhookAuthenticator {

    public static final String SECRET_HEADER = "X-TradingView-Secret";

    private final String configuredSecret;

    public SharedSecretWebhookAuthenticator(@Value("${trading.webhook.secret:}") String configuredSecret) {
        this.configuredSecret = configuredSecret;
    }

    @Override
    public void authenticate(HttpServletRequest request) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            // Fail closed: an unconfigured server accepts nothing rather than
            // silently trusting every caller.
            throw new InvalidWebhookSecretException("Webhook secret is not configured on the server");
        }

        String provided = request.getHeader(SECRET_HEADER);
        if (provided == null || provided.isBlank() || !constantTimeEquals(configuredSecret, provided)) {
            throw new InvalidWebhookSecretException("Invalid or missing " + SECRET_HEADER + " header");
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
