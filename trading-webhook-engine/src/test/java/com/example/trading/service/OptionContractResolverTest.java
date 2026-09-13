package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.FnoResolutionParams;
import com.example.trading.dto.OptionContract;
import com.example.trading.enums.OptionType;
import com.example.trading.exception.ContractNotFoundException;
import com.example.trading.exception.InvalidLotSizeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OptionContractResolverTest {

    private InstrumentMasterService instrumentMasterService;
    private TradingProperties properties;
    private OptionContractResolver resolver;

    private final LocalDate nearExpiry = LocalDate.now().plusDays(3);
    private final LocalDate nextExpiry = LocalDate.now().plusDays(10);

    @BeforeEach
    void setUp() {
        instrumentMasterService = mock(InstrumentMasterService.class);
        properties = new TradingProperties();
        resolver = new OptionContractResolver(instrumentMasterService, properties);
    }

    private InstrumentMasterService.InstrumentRow row(String symbol, LocalDate expiry, int strike, OptionType type) {
        return row(symbol, expiry, strike, type, 75);
    }

    private InstrumentMasterService.InstrumentRow row(String symbol, LocalDate expiry, int strike, OptionType type, int lotSize) {
        return new InstrumentMasterService.InstrumentRow(symbol, "NIFTY", expiry, BigDecimal.valueOf(strike), type, lotSize, "FNO", "NSE");
    }

    private FnoResolutionParams defaultParams() {
        return new FnoResolutionParams("CE", "NEAREST", "ATM", 0, 1, null);
    }

    private FnoResolutionParams paramsWithLotSize(Integer lotSize) {
        return new FnoResolutionParams("CE", "NEAREST", "ATM", 0, 1, lotSize);
    }

    @Test
    void resolve_pickAtmCallForNearestExpiry() {
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-NEAR-24900CE", nearExpiry, 24900, OptionType.CE),
                row("NIFTY-NEAR-25000CE", nearExpiry, 25000, OptionType.CE),
                row("NIFTY-NEAR-25100CE", nearExpiry, 25100, OptionType.CE),
                row("NIFTY-NEXT-25000CE", nextExpiry, 25000, OptionType.CE)
        ));

        OptionContract contract = resolver.resolve("NIFTY", BigDecimal.valueOf(25010));

        assertThat(contract.getTradingSymbol()).isEqualTo("NIFTY-NEAR-25000CE");
        assertThat(contract.getExpiry()).isEqualTo(nearExpiry);
        assertThat(contract.getOptionType()).isEqualTo(OptionType.CE);
        assertThat(contract.getLotSize()).isEqualTo(75);
        assertThat(contract.isBuyAllowed()).isTrue();
    }

    @Test
    void resolve_usesNextExpiry_whenConfigured() {
        properties.getOption().setExpirySelection("NEXT");
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-NEAR-25000CE", nearExpiry, 25000, OptionType.CE),
                row("NIFTY-NEXT-25000CE", nextExpiry, 25000, OptionType.CE)
        ));

        OptionContract contract = resolver.resolve("NIFTY", BigDecimal.valueOf(25000));

        assertThat(contract.getExpiry()).isEqualTo(nextExpiry);
    }

    @Test
    void resolve_fixedPe_whenConfigured() {
        properties.getOption().setOptionType("PE");
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-NEAR-25000CE", nearExpiry, 25000, OptionType.CE),
                row("NIFTY-NEAR-25000PE", nearExpiry, 25000, OptionType.PE)
        ));

        OptionContract contract = resolver.resolve("NIFTY", BigDecimal.valueOf(25000));

        assertThat(contract.getOptionType()).isEqualTo(OptionType.PE);
        assertThat(contract.getTradingSymbol()).isEqualTo("NIFTY-NEAR-25000PE");
    }

    @Test
    void resolve_itmOffsetForCall_movesToLowerStrike() {
        properties.getOption().setStrikeSelection("ITM");
        properties.getOption().setStrikeOffset(1);
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-24900CE", nearExpiry, 24900, OptionType.CE),
                row("NIFTY-25000CE", nearExpiry, 25000, OptionType.CE),
                row("NIFTY-25100CE", nearExpiry, 25100, OptionType.CE)
        ));

        OptionContract contract = resolver.resolve("NIFTY", BigDecimal.valueOf(25000));

        assertThat(contract.getTradingSymbol()).isEqualTo("NIFTY-24900CE");
    }

    @Test
    void resolve_throwsContractNotFound_whenNoInstrumentsForUnderlying() {
        when(instrumentMasterService.getOptionsForUnderlying(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolve("NIFTY", BigDecimal.valueOf(25000)))
                .isInstanceOf(ContractNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Lot size priority: user-selected value (validated) > contract default.
    // Never hardcoded - always sourced from InstrumentMasterService rows.
    // ------------------------------------------------------------------

    @Test
    void resolve_noUserLotSize_usesContractsOwnCurrentLotSize() {
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-25000CE", nearExpiry, 25000, OptionType.CE, 65)
        ));

        OptionContract contract = resolver.resolve("NIFTY", BigDecimal.valueOf(25000), defaultParams());

        assertThat(contract.getLotSize()).isEqualTo(65);
    }

    @Test
    void resolve_validUserLotSize_isUsedInsteadOfDefault() {
        // Simulates a lot-size revision window: two instrument rows for the
        // exact same underlying+expiry+strike+type, differing only in lot size.
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-25000CE-OLD", nearExpiry, 25000, OptionType.CE, 65),
                row("NIFTY-25000CE-NEW", nearExpiry, 25000, OptionType.CE, 75)
        ));

        OptionContract contract = resolver.resolve("NIFTY", BigDecimal.valueOf(25000), paramsWithLotSize(75));

        assertThat(contract.getLotSize()).isEqualTo(75);
        assertThat(contract.getTradingSymbol()).isEqualTo("NIFTY-25000CE-NEW");
    }

    @Test
    void resolve_invalidUserLotSize_throwsWithValidOptionsListed() {
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-25000CE", nearExpiry, 25000, OptionType.CE, 65)
        ));

        assertThatThrownBy(() -> resolver.resolve("NIFTY", BigDecimal.valueOf(25000), paramsWithLotSize(999)))
                .isInstanceOf(InvalidLotSizeException.class)
                .satisfies(ex -> {
                    InvalidLotSizeException lotEx = (InvalidLotSizeException) ex;
                    assertThat(lotEx.getRequestedLotSize()).isEqualTo(999);
                    assertThat(lotEx.getValidLotSizes()).containsExactly(65);
                });
    }

    @Test
    void resolve_contractChange_reResolvesLotSizeFromNewContract() {
        // Old contract (e.g. before the user changed strike) had lot size 65.
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-24900CE", nearExpiry, 24900, OptionType.CE, 65),
                row("NIFTY-25100CE", nearExpiry, 25100, OptionType.CE, 50)
        ));

        // Strike selection moves from ATM(24900-ish) to OTM, landing on the 25100 row - a
        // "new contract" with its own, different, current lot size.
        FnoResolutionParams otmParams = new FnoResolutionParams("CE", "NEAREST", "OTM", 1, 1, null);
        OptionContract contract = resolver.resolve("NIFTY", BigDecimal.valueOf(24900), otmParams);

        assertThat(contract.getTradingSymbol()).isEqualTo("NIFTY-25100CE");
        assertThat(contract.getLotSize()).isEqualTo(50);
    }

    @Test
    void validLotSizesFor_returnsDistinctSortedLotSizesForResolvedContract() {
        when(instrumentMasterService.getOptionsForUnderlying("NIFTY")).thenReturn(List.of(
                row("NIFTY-25000CE-A", nearExpiry, 25000, OptionType.CE, 75),
                row("NIFTY-25000CE-B", nearExpiry, 25000, OptionType.CE, 65)
        ));

        List<Integer> validLotSizes = resolver.validLotSizesFor("NIFTY", BigDecimal.valueOf(25000), defaultParams());

        assertThat(validLotSizes).containsExactly(65, 75);
    }
}
