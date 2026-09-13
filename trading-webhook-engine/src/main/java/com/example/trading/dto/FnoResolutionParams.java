package com.example.trading.dto;

/**
 * Everything {@link com.example.trading.service.OptionContractResolver} needs
 * to resolve one contract, other than the underlying/price. Carries either
 * the operator's per-underlying override (see
 * {@link com.example.trading.service.FnoTradeConfigService}) or the
 * server-config defaults ({@code trading.option.*} / {@code trading.quantity.lots})
 * - the resolver itself never reads config directly, so it has no notion of
 * "default" vs "user-selected" values; that decision is made once, here.
 *
 * @param lotSize null means "no explicit user selection - use the resolved
 *                contract's own current lot size"; non-null means the user
 *                selected a specific lot size, which MUST be validated
 *                against the resolved contract's actual valid lot size(s)
 *                before being trusted.
 */
public record FnoResolutionParams(String optionType, String expirySelection, String strikeSelection,
                                   int strikeOffset, int lots, Integer lotSize) {
}
