package com.example.trading.service;

import com.example.trading.enums.OptionType;
import com.example.trading.groww.GrowwApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstrumentMasterServiceTest {

    private GrowwApiClient growwApiClient;
    private InstrumentMasterService service;

    @BeforeEach
    void setUp() {
        growwApiClient = mock(GrowwApiClient.class);
        service = new InstrumentMasterService(growwApiClient);
    }

    @Test
    void parsesFnoOptionRows_andIgnoresOtherSegmentsAndFutures() {
        String csv = String.join("\n",
                "exchange,trading_symbol,underlying_symbol,segment,instrument_type,expiry_date,strike_price,lot_size",
                "NSE,NIFTY25SEP25000CE,NIFTY,FNO,CE,2026-09-25,25000,75",
                "NSE,NIFTY25SEP25000PE,NIFTY,FNO,PE,2026-09-25,25000,75",
                "NSE,NIFTYFUT,NIFTY,FNO,FUT,2026-09-25,0,75",
                "NSE,RELIANCE-EQ,RELIANCE,CASH,,,,1"
        );
        when(growwApiClient.fetchInstrumentMasterCsv()).thenReturn(csv);

        List<InstrumentMasterService.InstrumentRow> rows = service.getOptionsForUnderlying("NIFTY");

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(InstrumentMasterService.InstrumentRow::instrumentType)
                .containsExactlyInAnyOrder(OptionType.CE, OptionType.PE);
        assertThat(rows.get(0).lotSize()).isEqualTo(75);
        assertThat(rows.get(0).expiryDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(rows.get(0).strikePrice()).isEqualTo(BigDecimal.valueOf(25000));
    }

    @Test
    void isCaseInsensitiveOnUnderlying_andRobustToColumnReordering() {
        String csv = String.join("\n",
                "instrument_type,strike_price,expiry_date,lot_size,trading_symbol,underlying_symbol,segment,exchange",
                "CE,25000,2026-09-25,75,NIFTY25SEP25000CE,NIFTY,FNO,NSE"
        );
        when(growwApiClient.fetchInstrumentMasterCsv()).thenReturn(csv);

        List<InstrumentMasterService.InstrumentRow> rows = service.getOptionsForUnderlying("nifty");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).tradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
    }

    @Test
    void emptyCsv_returnsEmptyList() {
        when(growwApiClient.fetchInstrumentMasterCsv()).thenReturn("");
        assertThat(service.getOptionsForUnderlying("NIFTY")).isEmpty();
    }
}
