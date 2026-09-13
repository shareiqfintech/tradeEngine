package com.example.trading.groww;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the TOTP implementation against the RFC 6238 Appendix B SHA-1
 * test vector: seed "12345678901234567890" (ASCII), Base32-encoded as
 * "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", time step 30s, at T=59s (time-step
 * counter = 1) must produce the (8-digit, here truncated to 6) code
 * "94287082" -&gt; last 6 digits "287082". This is the single most widely
 * cited RFC 6238 conformance vector and is independent of wall-clock time,
 * so the test is fully deterministic.
 */
class TotpGeneratorTest {

    private static final String RFC6238_BASE32_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void generate_matchesRfc6238TestVector_atCounterOne() {
        String code = TotpGenerator.generate(RFC6238_BASE32_SECRET, 1L);
        assertThat(code).isEqualTo("287082");
    }

    @Test
    void generate_isDeterministic_forSameSecretAndCounter() {
        String first = TotpGenerator.generate(RFC6238_BASE32_SECRET, 42L);
        String second = TotpGenerator.generate(RFC6238_BASE32_SECRET, 42L);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void generate_producesSixDigitCode() {
        String code = TotpGenerator.generate(RFC6238_BASE32_SECRET, 12345L);
        assertThat(code).hasSize(6);
        assertThat(code).containsOnlyDigits();
    }

    @Test
    void generate_differentCounters_typicallyProduceDifferentCodes() {
        String a = TotpGenerator.generate(RFC6238_BASE32_SECRET, 1L);
        String b = TotpGenerator.generate(RFC6238_BASE32_SECRET, 2L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void currentCode_returnsSixDigitNumericString() {
        String code = TotpGenerator.currentCode(RFC6238_BASE32_SECRET);
        assertThat(code).hasSize(6);
        assertThat(code).containsOnlyDigits();
    }

    @Test
    void base32Decode_rejectsInvalidCharacters() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> TotpGenerator.base32Decode("not-valid-base32!!!"));
    }
}
