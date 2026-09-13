package com.example.trading.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generates order reference ids that satisfy Groww's documented
 * {@code order_reference_id} rule: an 8-20 character alphanumeric string
 * with at most two hyphens. Format: {@code TV-<UNDERLYING>-<8 char suffix>},
 * e.g. {@code TV-NIFTY-K3J9F2A1} - always exactly two hyphens, always within
 * the length bound regardless of underlying length.
 */
@Service
public class OrderReferenceGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SUFFIX_LENGTH = 8;
    private static final int MAX_UNDERLYING_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String generate(String underlying) {
        String sanitizedUnderlying = sanitize(underlying);
        String suffix = randomSuffix();
        return "TV-" + sanitizedUnderlying + "-" + suffix;
    }

    /**
     * DETERMINISTIC reference for a 15:10 safety-exit close order, so a
     * re-run of the close process (scheduler double-fire, monitor loop,
     * application restart in the 15:10-15:30 window) resolves to the exact
     * same reference and is de-duplicated instead of placing a second order.
     * Stable per {@code (sessionDate, userId, tradingSymbol)}.
     *
     * <p>Format: {@code SESSIONCLOSE-<yyyyMMdd>-<token>}, exactly two
     * hyphens. NOTE: this deliberately keeps the readable {@code SESSIONCLOSE}
     * prefix and therefore exceeds Groww's documented 8-20 char
     * {@code order_reference_id} bound; that is an accepted, explicit
     * trade-off (no length guard) - see the session-close spec.
     */
    public String sessionClose(LocalDate sessionDate, Long userId, String tradingSymbol) {
        String key = userId + "|" + (tradingSymbol == null ? "" : tradingSymbol.toUpperCase(Locale.ROOT));
        String token = Integer.toString(Math.abs(key.hashCode()), 36).toUpperCase(Locale.ROOT);
        return "SESSIONCLOSE-" + sessionDate.format(YYYYMMDD) + "-" + token;
    }

    /**
     * DETERMINISTIC reference for a target-hit exit order, stable per
     * {@code position_target.id}, so a rapid burst of LTP updates / a
     * retried exit / a restart mid-exit all resolve to the same reference
     * and are de-duplicated by the {@code orders.order_reference_id} unique
     * constraint instead of placing a second SELL. Format:
     * {@code TGTEXIT-<id>} - Groww-compliant (8-20 chars for realistic ids,
     * one hyphen, alphanumeric).
     */
    public String targetExit(Long positionTargetId) {
        return "TGTEXIT-" + positionTargetId;
    }

    private String sanitize(String underlying) {
        String cleaned = underlying == null ? "" : underlying.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return cleaned.substring(0, Math.min(cleaned.length(), MAX_UNDERLYING_LENGTH));
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
