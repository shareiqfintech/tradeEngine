package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.FnoResolutionParams;
import com.example.trading.dto.OptionContract;
import com.example.trading.enums.OptionType;
import com.example.trading.exception.ContractNotFoundException;
import com.example.trading.exception.InvalidLotSizeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Resolves an underlying + spot price into an exact, tradeable Groww option
 * contract: {@code underlying -> expiry -> strike -> CE/PE -> tradingSymbol + lotSize}.
 *
 * <p>Nothing here is hardcoded per underlying: available expiries, strikes,
 * lot size(s), and the exact trading symbol all come from
 * {@link InstrumentMasterService}, which mirrors Groww's instrument master.
 * In particular, lot size is NEVER assumed from a fixed mapping - see
 * {@link #selectLotSize}.
 *
 * <p><b>CE/PE choice when {@code option-type=AUTO}:</b> this engine follows
 * a directional option-buying convention - a BUY signal resolves to an ATM
 * CALL (bullish view), matching the common trend-following "buy calls on
 * up-signals" strategy this system is built for. A SELL signal never calls
 * this resolver at all (see {@code TradingEngineService}): it closes
 * whatever long option position already exists, using that position's own
 * trading symbol, not a freshly resolved contract. Operators who want a
 * fixed CE or PE regardless of signal direction can pin {@code option-type}
 * to {@code CE} or {@code PE}.
 */
@Slf4j
@Service
public class OptionContractResolver {

    private final InstrumentMasterService instrumentMasterService;
    private final TradingProperties properties;

    public OptionContractResolver(InstrumentMasterService instrumentMasterService, TradingProperties properties) {
        this.instrumentMasterService = instrumentMasterService;
        this.properties = properties;
    }

    /** Convenience overload using the server-config defaults (trading.option.*, trading.quantity.lots, no lot-size override). */
    public OptionContract resolve(String underlying, BigDecimal underlyingPrice) {
        return resolve(underlying, underlyingPrice, defaultParams());
    }

    /**
     * @param underlying      e.g. "NIFTY"
     * @param underlyingPrice current spot/signal price, used for ATM strike selection
     * @param params          resolution settings - either the operator's per-underlying
     *                        override (see FnoTradeConfigService) or the server-config
     *                        defaults; {@code params.lotSize()} being non-null means the
     *                        user explicitly selected a lot size, which must be validated
     *                        against the resolved contract's real lot size(s).
     */
    public OptionContract resolve(String underlying, BigDecimal underlyingPrice, FnoResolutionParams params) {
        List<InstrumentMasterService.InstrumentRow> rows = instrumentMasterService.getOptionsForUnderlying(underlying);
        if (rows.isEmpty()) {
            throw new ContractNotFoundException("No F&O option instruments found for underlying " + underlying);
        }

        OptionType optionType = resolveOptionType(params.optionType());
        LocalDate expiry = resolveExpiry(rows, params.expirySelection());

        List<InstrumentMasterService.InstrumentRow> forExpiryAndType = rows.stream()
                .filter(r -> r.expiryDate().equals(expiry) && r.instrumentType() == optionType)
                .toList();
        if (forExpiryAndType.isEmpty()) {
            throw new ContractNotFoundException(
                    "No " + optionType + " contracts found for " + underlying + " expiry=" + expiry);
        }

        SortedSet<BigDecimal> strikes = new TreeSet<>(Comparator.naturalOrder());
        forExpiryAndType.forEach(r -> strikes.add(r.strikePrice()));

        BigDecimal strike = resolveStrike(strikes, underlyingPrice, optionType, params.strikeSelection(), params.strikeOffset());

        List<InstrumentMasterService.InstrumentRow> forStrike = forExpiryAndType.stream()
                .filter(r -> r.strikePrice().compareTo(strike) == 0)
                .toList();
        if (forStrike.isEmpty()) {
            throw new ContractNotFoundException(
                    "Resolved strike " + strike + " has no matching instrument for " + underlying);
        }

        InstrumentMasterService.InstrumentRow resolvedRow = selectLotSize(forStrike, params.lotSize());

        OptionContract contract = OptionContract.builder()
                .tradingSymbol(resolvedRow.tradingSymbol())
                .underlying(underlying)
                .expiry(resolvedRow.expiryDate())
                .strike(resolvedRow.strikePrice())
                .optionType(resolvedRow.instrumentType())
                .lotSize(resolvedRow.lotSize())
                .exchange(resolvedRow.exchange())
                .segment(resolvedRow.segment())
                .buyAllowed(true)
                .sellAllowed(true)
                .build();

        validate(contract);
        return contract;
    }

    /** All valid lot sizes Groww's instrument master reports for the exact contract underlying+expiry+strike+optionType resolves to right now - used to populate the frontend's lot-size selector and to validate a user-selected value. */
    public List<Integer> validLotSizesFor(String underlying, BigDecimal underlyingPrice, FnoResolutionParams params) {
        List<InstrumentMasterService.InstrumentRow> rows = instrumentMasterService.getOptionsForUnderlying(underlying);
        OptionType optionType = resolveOptionType(params.optionType());
        LocalDate expiry = resolveExpiry(rows, params.expirySelection());

        List<InstrumentMasterService.InstrumentRow> forExpiryAndType = rows.stream()
                .filter(r -> r.expiryDate().equals(expiry) && r.instrumentType() == optionType)
                .toList();
        if (forExpiryAndType.isEmpty()) {
            return List.of();
        }
        SortedSet<BigDecimal> strikes = new TreeSet<>(Comparator.naturalOrder());
        forExpiryAndType.forEach(r -> strikes.add(r.strikePrice()));
        BigDecimal strike = resolveStrike(strikes, underlyingPrice, optionType, params.strikeSelection(), params.strikeOffset());

        return forExpiryAndType.stream()
                .filter(r -> r.strikePrice().compareTo(strike) == 0)
                .map(InstrumentMasterService.InstrumentRow::lotSize)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Priority: (1) user-selected lot size, validated against the rows Groww's
     * instrument master actually has for this exact contract; (2) the
     * resolved contract's own current lot size. Never a hardcoded value.
     */
    private InstrumentMasterService.InstrumentRow selectLotSize(List<InstrumentMasterService.InstrumentRow> candidates,
                                                                  Integer userLotSize) {
        if (userLotSize == null) {
            return candidates.get(0);
        }
        return candidates.stream()
                .filter(r -> r.lotSize() == userLotSize)
                .findFirst()
                .orElseThrow(() -> new InvalidLotSizeException(
                        userLotSize,
                        candidates.stream().map(InstrumentMasterService.InstrumentRow::lotSize).distinct().sorted().toList()));
    }

    private FnoResolutionParams defaultParams() {
        return new FnoResolutionParams(
                properties.getOption().getOptionType(),
                properties.getOption().getExpirySelection(),
                properties.getOption().getStrikeSelection(),
                properties.getOption().getStrikeOffset(),
                properties.getQuantity().getLots(),
                null);
    }

    private void validate(OptionContract contract) {
        if (contract.getTradingSymbol() == null || contract.getTradingSymbol().isBlank()) {
            throw new ContractNotFoundException("Resolved contract has no trading symbol");
        }
        if (contract.getLotSize() <= 0) {
            throw new ContractNotFoundException("Resolved contract " + contract.getTradingSymbol()
                    + " has an invalid lot size: " + contract.getLotSize());
        }
        if (!contract.isBuyAllowed()) {
            throw new ContractNotFoundException("Resolved contract " + contract.getTradingSymbol() + " does not allow BUY orders");
        }
    }

    private OptionType resolveOptionType(String configured) {
        if ("CE".equalsIgnoreCase(configured)) {
            return OptionType.CE;
        }
        if ("PE".equalsIgnoreCase(configured)) {
            return OptionType.PE;
        }
        return OptionType.CE; // AUTO -> CE, see class-level javadoc
    }

    private LocalDate resolveExpiry(List<InstrumentMasterService.InstrumentRow> rows, String expirySelection) {
        LocalDate today = LocalDate.now(properties.getZoneId());
        SortedSet<LocalDate> expiries = new TreeSet<>();
        rows.stream().map(InstrumentMasterService.InstrumentRow::expiryDate)
                .filter(date -> !date.isBefore(today))
                .forEach(expiries::add);

        if (expiries.isEmpty()) {
            throw new ContractNotFoundException("No upcoming expiries found");
        }

        boolean useNext = "NEXT".equalsIgnoreCase(expirySelection);
        if (useNext && expiries.size() > 1) {
            return expiries.stream().skip(1).findFirst().orElseThrow();
        }
        return expiries.first();
    }

    private BigDecimal resolveStrike(SortedSet<BigDecimal> strikes, BigDecimal underlyingPrice, OptionType optionType,
                                      String strikeSelection, int strikeOffset) {
        BigDecimal[] sorted = strikes.toArray(new BigDecimal[0]);

        int atmIndex = 0;
        BigDecimal smallestDiff = null;
        for (int i = 0; i < sorted.length; i++) {
            BigDecimal diff = sorted[i].subtract(underlyingPrice).abs();
            if (smallestDiff == null || diff.compareTo(smallestDiff) < 0) {
                smallestDiff = diff;
                atmIndex = i;
            }
        }

        int steps = strikeDirection(optionType, strikeSelection) * strikeOffset;
        int targetIndex = Math.max(0, Math.min(sorted.length - 1, atmIndex + steps));
        return sorted[targetIndex];
    }

    /**
     * Direction (in units of "index into the ascending strike list") that
     * moves AWAY from the money for the configured {@code strike-selection}.
     * ITM for a CALL means lower strikes; ITM for a PUT means higher strikes
     * - and OTM is the mirror image. {@code strike-offset} is then applied
     * in that direction.
     */
    private int strikeDirection(OptionType optionType, String strikeSelection) {
        if (strikeSelection == null || "ATM".equalsIgnoreCase(strikeSelection)) {
            return 0;
        }
        boolean itm = strikeSelection.toUpperCase().startsWith("ITM");
        boolean callLikeDirectionIsDown = optionType == OptionType.CE;
        if (itm) {
            return callLikeDirectionIsDown ? -1 : 1;
        }
        // OTM
        return callLikeDirectionIsDown ? 1 : -1;
    }
}
