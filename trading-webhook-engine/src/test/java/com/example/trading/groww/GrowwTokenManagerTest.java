package com.example.trading.groww;

import com.example.trading.enums.GrowwAuthState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Backed by a fake Redis (a plain Map behind mocked StringRedisTemplate
 * calls, same pattern as SignalDeduplicationServiceTest) rather than an
 * embedded/real Redis - fast, dependency-free, and enough to exercise the
 * manager's own read-your-own-write logic across calls within a test.
 */
class GrowwTokenManagerTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    private Map<String, String> fakeRedis;
    private GrowwTokenManager manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        fakeRedis = new HashMap<>();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(anyString())).thenAnswer(inv -> fakeRedis.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            fakeRedis.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> fakeRedis.remove(inv.getArgument(0, String.class)) != null);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        manager = new GrowwTokenManager(redisTemplate, objectMapper);
    }

    @Test
    void freshManager_startsInAuthRequired() {
        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.AUTH_REQUIRED);
        assertThat(manager.isUsable(USER_A)).isFalse();
        assertThat(manager.getToken(USER_A)).isEmpty();
    }

    @Test
    void storeToken_makesItUsable() {
        manager.storeToken(USER_A, "abc123", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(manager.isUsable(USER_A)).isTrue();
        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.AUTHENTICATED);
        assertThat(manager.getToken(USER_A)).contains("abc123");
    }

    @Test
    void expiredToken_isNotUsable_andStateBecomesTokenExpired() {
        manager.storeToken(USER_A, "abc123", Instant.now().minus(1, ChronoUnit.SECONDS));

        assertThat(manager.isUsable(USER_A)).isFalse();
        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.TOKEN_EXPIRED);
        assertThat(manager.getToken(USER_A)).isEmpty();
    }

    @Test
    void markFailed_setsAuthFailedState() {
        manager.markFailed(USER_A);

        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.AUTH_FAILED);
        assertThat(manager.isUsable(USER_A)).isFalse();
    }

    @Test
    void markExpired_setsTokenExpiredState_andClearsToken() {
        manager.storeToken(USER_A, "abc123", Instant.now().plus(1, ChronoUnit.HOURS));
        manager.markExpired(USER_A);

        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.TOKEN_EXPIRED);
        assertThat(manager.getToken(USER_A)).isEmpty();
    }

    @Test
    void clear_resetsToAuthRequired() {
        manager.storeToken(USER_A, "abc123", Instant.now().plus(1, ChronoUnit.HOURS));
        manager.clear(USER_A);

        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.AUTH_REQUIRED);
        assertThat(manager.isUsable(USER_A)).isFalse();
    }

    @Test
    void storeToken_withNullExpiry_fallsBackToDailyDefault_andIsImmediatelyUsable() {
        manager.storeToken(USER_A, "abc123", null);

        assertThat(manager.isUsable(USER_A)).isTrue();
    }

    // ------------------------------------------------------------------
    // Multi-user isolation (spec "Test 5: User A's token cannot overwrite User B's token")
    // ------------------------------------------------------------------

    @Test
    void twoUsersTokens_areCompletelyIndependent() {
        manager.storeToken(USER_A, "token-for-user-a", Instant.now().plus(1, ChronoUnit.HOURS));
        manager.storeToken(USER_B, "token-for-user-b", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(manager.getToken(USER_A)).contains("token-for-user-a");
        assertThat(manager.getToken(USER_B)).contains("token-for-user-b");
    }

    @Test
    void clearingOneUsersToken_neverAffectsAnotherUsers() {
        manager.storeToken(USER_A, "token-for-user-a", Instant.now().plus(1, ChronoUnit.HOURS));
        manager.storeToken(USER_B, "token-for-user-b", Instant.now().plus(1, ChronoUnit.HOURS));

        manager.clear(USER_A);

        assertThat(manager.getToken(USER_A)).isEmpty();
        assertThat(manager.isUsable(USER_B)).isTrue();
        assertThat(manager.getToken(USER_B)).contains("token-for-user-b");
    }

    @Test
    void userBHasNoToken_neverSeesUserAsToken() {
        manager.storeToken(USER_A, "token-for-user-a", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(manager.getToken(USER_B)).isEmpty();
        assertThat(manager.isUsable(USER_B)).isFalse();
        assertThat(manager.getState(USER_B)).isEqualTo(GrowwAuthState.AUTH_REQUIRED);
    }
}
