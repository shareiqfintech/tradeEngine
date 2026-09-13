package com.example.trading.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TARGET PRICE = actual filled entry price + Target Points. The TradingView
 * signal price is never an input. BigDecimal throughout.
 */
class TargetCalculatorTest {

    private final TargetCalculator calculator = new TargetCalculator();
    private static final BigDecimal TICK = new BigDecimal("0.05");

    @Test
    void points8_entry142_target150() {
        assertThat(calculator.targetPrice(new BigDecimal("142"), new BigDecimal("8"), TICK))
                .isEqualByComparingTo("150.00");
    }

    @Test
    void points10_entry142_target152() {
        assertThat(calculator.targetPrice(new BigDecimal("142"), new BigDecimal("10"), TICK))
                .isEqualByComparingTo("152.00");
    }

    @Test
    void decimalEntry_142_25_points8_target150_25() {
        assertThat(calculator.targetPrice(new BigDecimal("142.25"), new BigDecimal("8"), TICK))
                .isEqualByComparingTo("150.25");
    }

    @Test
    void entry99_50_points10_target109_50() {
        assertThat(calculator.targetPrice(new BigDecimal("99.50"), new BigDecimal("10"), TICK))
                .isEqualByComparingTo("109.50");
    }

    @Test
    void tradingViewPriceIsIrrelevant_onlyEntryAndPointsMatter() {
        // Whatever TradingView said (e.g. 141.50), the calculator only ever sees the ACTUAL fill.
        BigDecimal fromActualFill = calculator.targetPrice(new BigDecimal("142.20"), new BigDecimal("8"), TICK);
        assertThat(fromActualFill).isEqualByComparingTo("150.20");
    }

    @Test
    void offTickResult_isNormalizedToTheNearestTick() {
        // 100.02 + 8 = 108.02 -> nearest 0.05 tick = 108.00
        assertThat(calculator.targetPrice(new BigDecimal("100.02"), new BigDecimal("8"), TICK))
                .isEqualByComparingTo("108.00");
        // 100.04 + 8 = 108.04 -> nearest 0.05 tick = 108.05
        assertThat(calculator.targetPrice(new BigDecimal("100.04"), new BigDecimal("8"), TICK))
                .isEqualByComparingTo("108.05");
    }

    @Test
    void tickNormalizationCanBeDisabled() {
        assertThat(calculator.targetPrice(new BigDecimal("100.02"), new BigDecimal("8"), null))
                .isEqualByComparingTo("108.02");
        assertThat(calculator.targetPrice(new BigDecimal("100.02"), new BigDecimal("8"), BigDecimal.ZERO))
                .isEqualByComparingTo("108.02");
    }

    @Test
    void rejectsNonPositiveEntryOrPoints() {
        assertThatThrownBy(() -> calculator.targetPrice(BigDecimal.ZERO, new BigDecimal("8"), TICK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.targetPrice(new BigDecimal("142"), BigDecimal.ZERO, TICK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.targetPrice(new BigDecimal("142"), new BigDecimal("-1"), TICK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
