package com.example.trading.groww;

import com.example.trading.enums.GrowwAuthState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Holds each user's current Groww access token in Redis - never on disk as
 * plaintext config, never logged, never returned by any REST endpoint.
 * Keyed by application {@code userId} (Redis key "groww:token:{userId}"),
 * so User A's token can never be read as, or overwritten by, User B's.
 *
 * <p>Backed by Redis rather than a process-local map so the token survives
 * this service's own restarts (e.g. a serverless host recycling instances
 * between requests) - previously an in-memory-only cache meant every
 * restart silently lost every user's session, forcing a re-login even
 * though nothing about their stored API key/TOTP secret had changed.
 *
 * <p>Groww does not always return an explicit token expiry (the
 * {@code v1/token/api/access} response's {@code expiry} field is optional
 * depending on key type); when it is absent this class falls back to the
 * documented behaviour for daily-approval access tokens - "expires daily at
 * 6:00 AM IST" - so a token is never trusted for longer than that even if
 * Groww didn't say so explicitly.
 */
@Slf4j
@Component
public class GrowwTokenManager {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime DEFAULT_DAILY_EXPIRY = LocalTime.of(6, 0);
    private static final TokenState AUTH_REQUIRED_STATE = new TokenState(null, null, null, GrowwAuthState.AUTH_REQUIRED);
    private static final String KEY_PREFIX = "groww:token:";
    // Safety-net TTL so a user who never comes back doesn't leave a Redis key
    // forever; well past the longest any stored state is ever actually valid for.
    private static final Duration REDIS_SAFETY_TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GrowwTokenManager(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** Stores a freshly-issued token for {@code userId}. {@code expiresAt} may be null if Groww did not supply one. */
    public void storeToken(Long userId, String accessToken, Instant expiresAt) {
        Instant effectiveExpiry = expiresAt != null ? expiresAt : nextDailyExpiryFromNow();
        write(userId, new TokenState(accessToken, Instant.now(), effectiveExpiry, GrowwAuthState.AUTHENTICATED));
        log.info("GROWW_AUTH_STATE_CHANGED userId={} newState=AUTHENTICATED expiresAt={}", userId, effectiveExpiry);
    }

    public Optional<String> getToken(Long userId) {
        TokenState current = currentState(userId);
        if (current.state != GrowwAuthState.AUTHENTICATED) {
            return Optional.empty();
        }
        if (isExpired(current)) {
            markExpired(userId);
            return Optional.empty();
        }
        return Optional.ofNullable(current.accessToken);
    }

    /** True only when a non-expired token exists for {@code userId} and the state machine says AUTHENTICATED. */
    public boolean isUsable(Long userId) {
        TokenState current = currentState(userId);
        if (current.state != GrowwAuthState.AUTHENTICATED || current.accessToken == null) {
            return false;
        }
        if (isExpired(current)) {
            markExpired(userId);
            return false;
        }
        return true;
    }

    public GrowwAuthState getState(Long userId) {
        TokenState current = currentState(userId);
        if (current.state == GrowwAuthState.AUTHENTICATED && isExpired(current)) {
            markExpired(userId);
            return GrowwAuthState.TOKEN_EXPIRED;
        }
        return current.state;
    }

    public void markFailed(Long userId) {
        write(userId, new TokenState(null, null, null, GrowwAuthState.AUTH_FAILED));
        log.warn("GROWW_AUTH_STATE_CHANGED userId={} newState=AUTH_FAILED", userId);
    }

    public void markExpired(Long userId) {
        write(userId, new TokenState(null, null, null, GrowwAuthState.TOKEN_EXPIRED));
        log.warn("GROWW_AUTH_STATE_CHANGED userId={} newState=TOKEN_EXPIRED", userId);
    }

    /** Full reset back to AUTH_REQUIRED for one user (e.g. their credentials changed, or an explicit logout/admin action). */
    public void clear(Long userId) {
        redisTemplate.delete(key(userId));
        log.info("GROWW_AUTH_STATE_CHANGED userId={} newState=AUTH_REQUIRED", userId);
    }

    private TokenState currentState(Long userId) {
        String raw = redisTemplate.opsForValue().get(key(userId));
        if (raw == null) {
            return AUTH_REQUIRED_STATE;
        }
        try {
            return objectMapper.readValue(raw, TokenState.class);
        } catch (Exception ex) {
            log.warn("GROWW_TOKEN_DESERIALIZE_FAILED userId={} reason={}", userId, ex.getMessage());
            return AUTH_REQUIRED_STATE;
        }
    }

    private void write(Long userId, TokenState state) {
        try {
            redisTemplate.opsForValue().set(key(userId), objectMapper.writeValueAsString(state), REDIS_SAFETY_TTL);
        } catch (Exception ex) {
            log.error("GROWW_TOKEN_WRITE_FAILED userId={} reason={}", userId, ex.getMessage());
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private boolean isExpired(TokenState tokenState) {
        return tokenState.expiresAt != null && Instant.now().isAfter(tokenState.expiresAt);
    }

    private Instant nextDailyExpiryFromNow() {
        ZonedDateTime now = ZonedDateTime.now(IST);
        ZonedDateTime todayExpiry = ZonedDateTime.of(LocalDate.now(IST), DEFAULT_DAILY_EXPIRY, IST);
        ZonedDateTime expiry = now.isBefore(todayExpiry) ? todayExpiry : todayExpiry.plusDays(1);
        return expiry.toInstant();
    }

    private record TokenState(String accessToken, Instant issuedAt, Instant expiresAt, GrowwAuthState state) {
    }
}
