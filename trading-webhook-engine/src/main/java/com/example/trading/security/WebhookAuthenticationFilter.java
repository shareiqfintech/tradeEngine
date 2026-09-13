package com.example.trading.security;

import com.example.trading.dto.ErrorResponse;
import com.example.trading.exception.InvalidWebhookSecretException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces webhook authentication BEFORE the request body is read or
 * validated, matching the required order of operations:
 * (1) authenticate, (2) parse/validate JSON, (3) validate signal, ...
 *
 * <p>Scoped to {@code /api/webhook/**} only - it never touches admin or
 * status endpoints, which have their own protection.
 */
@Slf4j
@Component
public class WebhookAuthenticationFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH_PREFIX = "/api/webhook";

    private final WebhookAuthenticator webhookAuthenticator;
    private final ObjectMapper objectMapper;

    public WebhookAuthenticationFilter(WebhookAuthenticator webhookAuthenticator, ObjectMapper objectMapper) {
        this.webhookAuthenticator = webhookAuthenticator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            webhookAuthenticator.authenticate(request);
        } catch (InvalidWebhookSecretException ex) {
            log.warn("WEBHOOK_AUTH_FAILED path={} remoteAddr={}", request.getRequestURI(), request.getRemoteAddr());
            writeUnauthorized(response, request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Invalid or missing webhook secret", path);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
