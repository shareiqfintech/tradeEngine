package com.example.trading.service;

import com.example.trading.dto.Position;
import com.example.trading.enums.OptionType;
import com.example.trading.exception.PositionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PositionServiceTest {

    private static final Long USER_ID = 1L;

    private GrowwPositionService growwPositionService;
    private AuditService auditService;
    private PositionService service;

    @BeforeEach
    void setUp() {
        growwPositionService = mock(GrowwPositionService.class);
        auditService = mock(AuditService.class);
        service = new PositionService(growwPositionService, auditService);
    }

    @Test
    void resolveLongPositionForSell_returnsPosition_whenFound() {
        Position position = Position.builder().tradingSymbol("NIFTY25SEP25000CE").netQuantity(75).build();
        when(growwPositionService.findLongPosition(USER_ID, "NIFTY", "SIG-1")).thenReturn(Optional.of(position));

        Position result = service.resolveLongPositionForSell(USER_ID, "NIFTY", "SIG-1");

        assertThat(result.getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
    }

    @Test
    void resolveLongPositionForSell_throwsNoLongPosition_whenAbsent() {
        when(growwPositionService.findLongPosition(USER_ID, "NIFTY", "SIG-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveLongPositionForSell(USER_ID, "NIFTY", "SIG-1"))
                .isInstanceOf(PositionNotFoundException.class)
                .hasMessageContaining("NO_LONG_POSITION");
    }

    @Test
    void availableQuantityToSell_neverNegative() {
        Position shortPosition = Position.builder().netQuantity(-75).build();
        assertThat(service.availableQuantityToSell(shortPosition)).isEqualTo(0);

        Position longPosition = Position.builder().netQuantity(75).build();
        assertThat(service.availableQuantityToSell(longPosition)).isEqualTo(75);
    }

    @Test
    void findActivePosition_delegatesToGrowwPositionService() {
        Position pePosition = Position.builder().tradingSymbol("NIFTY25SEP25000PE").netQuantity(260).build();
        when(growwPositionService.findActivePosition(USER_ID, "NIFTY", OptionType.PE, "SIG-1")).thenReturn(Optional.of(pePosition));

        Optional<Position> result = service.findActivePosition(USER_ID, "NIFTY", OptionType.PE, "SIG-1");

        assertThat(result).isPresent();
        assertThat(result.get().getTradingSymbol()).isEqualTo("NIFTY25SEP25000PE");
    }
}
