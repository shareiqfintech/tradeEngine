package com.example.trading.service;

import com.example.trading.exception.InvalidWebhookSecretException;
import com.example.trading.security.SharedSecretWebhookAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the shared-secret authenticator, independent of Spring MVC.
 */
class SharedSecretWebhookAuthenticatorTest {

    @Test
    void validSecret_doesNotThrow() {
        SharedSecretWebhookAuthenticator authenticator = new SharedSecretWebhookAuthenticator("my-secret");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-TradingView-Secret")).thenReturn("my-secret");

        authenticator.authenticate(request); // should not throw
    }

    @Test
    void wrongSecret_throws() {
        SharedSecretWebhookAuthenticator authenticator = new SharedSecretWebhookAuthenticator("my-secret");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-TradingView-Secret")).thenReturn("wrong-secret");

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(InvalidWebhookSecretException.class);
    }

    @Test
    void missingHeader_throws() {
        SharedSecretWebhookAuthenticator authenticator = new SharedSecretWebhookAuthenticator("my-secret");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-TradingView-Secret")).thenReturn(null);

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(InvalidWebhookSecretException.class);
    }

    @Test
    void serverWithNoConfiguredSecret_rejectsEverything() {
        SharedSecretWebhookAuthenticator authenticator = new SharedSecretWebhookAuthenticator("");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-TradingView-Secret")).thenReturn("anything");

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(InvalidWebhookSecretException.class);
    }

    @Test
    void exceptionMessage_neverContainsSecretValue() {
        SharedSecretWebhookAuthenticator authenticator = new SharedSecretWebhookAuthenticator("top-secret-value");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-TradingView-Secret")).thenReturn("guessed-value");

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(InvalidWebhookSecretException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).doesNotContain("top-secret-value");
                    assertThat(ex.getMessage()).doesNotContain("guessed-value");
                });
    }
}
