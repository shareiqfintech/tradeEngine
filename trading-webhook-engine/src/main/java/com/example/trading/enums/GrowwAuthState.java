package com.example.trading.enums;

/**
 * State machine for {@code GrowwTokenManager} / {@code GrowwAuthenticationService}.
 *
 * <pre>
 * AUTH_REQUIRED  -- authenticate() succeeds --&gt; AUTHENTICATED
 * AUTH_REQUIRED  -- authenticate() fails    --&gt; AUTH_FAILED
 * AUTH_FAILED    -- authenticate() retried  --&gt; AUTHENTICATED | AUTH_FAILED
 * AUTHENTICATED  -- token expiry reached    --&gt; TOKEN_EXPIRED
 * AUTHENTICATED  -- broker 401/403          --&gt; TOKEN_EXPIRED
 * TOKEN_EXPIRED  -- authenticate() succeeds --&gt; AUTHENTICATED
 * </pre>
 */
public enum GrowwAuthState {
    AUTHENTICATED,
    AUTH_REQUIRED,
    AUTH_FAILED,
    TOKEN_EXPIRED
}
