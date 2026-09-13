package com.example.trading.groww;

import com.example.trading.enums.GrowwAuthState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GrowwTokenManagerTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    @Test
    void freshManager_startsInAuthRequired() {
        GrowwTokenManager manager = new GrowwTokenManager();
        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.AUTH_REQUIRED);
        assertThat(manager.isUsable(USER_A)).isFalse();
        assertThat(manager.getToken(USER_A)).isEmpty();
    }

    @Test
    void storeToken_makesItUsable() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.storeToken(USER_A, "abc123", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(manager.isUsable(USER_A)).isTrue();
        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.AUTHENTICATED);
        assertThat(manager.getToken(USER_A)).contains("abc123");
    }

    @Test
    void expiredToken_isNotUsable_andStateBecomesTokenExpired() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.storeToken(USER_A, "abc123", Instant.now().minus(1, ChronoUnit.SECONDS));

        assertThat(manager.isUsable(USER_A)).isFalse();
        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.TOKEN_EXPIRED);
        assertThat(manager.getToken(USER_A)).isEmpty();
    }

    @Test
    void markFailed_setsAuthFailedState() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.markFailed(USER_A);

        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.AUTH_FAILED);
        assertThat(manager.isUsable(USER_A)).isFalse();
    }

    @Test
    void markExpired_setsTokenExpiredState_andClearsToken() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.storeToken(USER_A, "abc123", Instant.now().plus(1, ChronoUnit.HOURS));
        manager.markExpired(USER_A);

        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.TOKEN_EXPIRED);
        assertThat(manager.getToken(USER_A)).isEmpty();
    }

    @Test
    void clear_resetsToAuthRequired() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.storeToken(USER_A, "abc123", Instant.now().plus(1, ChronoUnit.HOURS));
        manager.clear(USER_A);

        assertThat(manager.getState(USER_A)).isEqualTo(GrowwAuthState.AUTH_REQUIRED);
        assertThat(manager.isUsable(USER_A)).isFalse();
    }

    @Test
    void storeToken_withNullExpiry_fallsBackToDailyDefault_andIsImmediatelyUsable() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.storeToken(USER_A, "abc123", null);

        assertThat(manager.isUsable(USER_A)).isTrue();
    }

    // ------------------------------------------------------------------
    // Multi-user isolation (spec "Test 5: User A's token cannot overwrite User B's token")
    // ------------------------------------------------------------------

    @Test
    void twoUsersTokens_areCompletelyIndependent() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.storeToken(USER_A, "token-for-user-a", Instant.now().plus(1, ChronoUnit.HOURS));
        manager.storeToken(USER_B, "token-for-user-b", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(manager.getToken(USER_A)).contains("token-for-user-a");
        assertThat(manager.getToken(USER_B)).contains("token-for-user-b");
    }

    @Test
    void clearingOneUsersToken_neverAffectsAnotherUsers() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.storeToken(USER_A, "token-for-user-a", Instant.now().plus(1, ChronoUnit.HOURS));
        manager.storeToken(USER_B, "token-for-user-b", Instant.now().plus(1, ChronoUnit.HOURS));

        manager.clear(USER_A);

        assertThat(manager.getToken(USER_A)).isEmpty();
        assertThat(manager.isUsable(USER_B)).isTrue();
        assertThat(manager.getToken(USER_B)).contains("token-for-user-b");
    }

    @Test
    void userBHasNoToken_neverSeesUserAsToken() {
        GrowwTokenManager manager = new GrowwTokenManager();
        manager.storeToken(USER_A, "token-for-user-a", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(manager.getToken(USER_B)).isEmpty();
        assertThat(manager.isUsable(USER_B)).isFalse();
        assertThat(manager.getState(USER_B)).isEqualTo(GrowwAuthState.AUTH_REQUIRED);
    }
}
