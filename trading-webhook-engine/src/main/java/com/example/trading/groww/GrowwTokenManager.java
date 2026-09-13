package com.example.trading.groww;

import com.example.trading.enums.GrowwAuthState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds each user's current Groww access token in memory only - never on
 * disk, never logged, never returned by any REST endpoint. Keyed by
 * application {@code userId}, so User A's token can never be read as, or
 * overwritten by, User B's: there is no single shared mutable token field
 * anywhere in this class, only per-user map entries.
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

    private final Map<Long, TokenState> tokensByUserId = new ConcurrentHashMap<>();

    /** Stores a freshly-issued token for {@code userId}. {@code expiresAt} may be null if Groww did not supply one. */
    public void storeToken(Long userId, String accessToken, Instant expiresAt) {
        Instant effectiveExpiry = expiresAt != null ? expiresAt : nextDailyExpiryFromNow();
        tokensByUserId.put(userId, new TokenState(accessToken, Instant.now(), effectiveExpiry, GrowwAuthState.AUTHENTICATED));
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
        tokensByUserId.put(userId, new TokenState(null, null, null, GrowwAuthState.AUTH_FAILED));
        log.warn("GROWW_AUTH_STATE_CHANGED userId={} newState=AUTH_FAILED", userId);
    }

    public void markExpired(Long userId) {
        tokensByUserId.put(userId, new TokenState(null, null, null, GrowwAuthState.TOKEN_EXPIRED));
        log.warn("GROWW_AUTH_STATE_CHANGED userId={} newState=TOKEN_EXPIRED", userId);
    }

    /** Full reset back to AUTH_REQUIRED for one user (e.g. their credentials changed, or an explicit logout/admin action). */
    public void clear(Long userId) {
        tokensByUserId.remove(userId);
        log.info("GROWW_AUTH_STATE_CHANGED userId={} newState=AUTH_REQUIRED", userId);
    }

    private TokenState currentState(Long userId) {
        return tokensByUserId.getOrDefault(userId, AUTH_REQUIRED_STATE);
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
