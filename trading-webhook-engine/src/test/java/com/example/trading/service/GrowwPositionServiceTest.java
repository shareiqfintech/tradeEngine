package com.example.trading.service;

import com.example.trading.dto.Position;
import com.example.trading.enums.OptionType;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.groww.dto.GrowwPositionDto;
import com.example.trading.repository.PositionSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrowwPositionServiceTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    private GrowwApiClient growwApiClient;
    private PositionSnapshotRepository snapshotRepository;
    private AuditService auditService;
    private GrowwPositionService service;

    @BeforeEach
    void setUp() {
        growwApiClient = mock(GrowwApiClient.class);
        snapshotRepository = mock(PositionSnapshotRepository.class);
        auditService = mock(AuditService.class);
        service = new GrowwPositionService(growwApiClient, snapshotRepository, auditService);
    }

    private GrowwPositionDto dto(String symbol, Integer quantity, Integer carryForward) {
        GrowwPositionDto dto = new GrowwPositionDto();
        dto.setTradingSymbol(symbol);
        dto.setExchange("NSE");
        dto.setProduct("NRML");
        dto.setQuantity(quantity);
        dto.setNetCarryForwardQuantity(carryForward);
        return dto;
    }

    @Test
    void getPositions_mapsNetQuantityAsQuantityPlusCarryForward() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of(dto("NIFTY25SEP25000CE", 75, 0)));

        List<Position> positions = service.getPositions(USER_A);

        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).getNetQuantity()).isEqualTo(75);
        assertThat(positions.get(0).isLong()).isTrue();
    }

    @Test
    void findLongPosition_matchesByUnderlyingPrefixAndPositiveNetQuantity() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of(
                dto("BANKNIFTY25SEP50000CE", 25, 0),
                dto("NIFTY25SEP25000CE", 75, 0)
        ));

        Optional<Position> found = service.findLongPosition(USER_A, "NIFTY", "SIG-1");

        assertThat(found).isPresent();
        assertThat(found.get().getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
    }

    @Test
    void findLongPosition_ignoresFlatOrShortPositions() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of(dto("NIFTY25SEP25000CE", 0, 0)));

        Optional<Position> found = service.findLongPosition(USER_A, "NIFTY", "SIG-1");

        assertThat(found).isEmpty();
    }

    @Test
    void findLongPosition_noMatch_returnsEmpty() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of());

        assertThat(service.findLongPosition(USER_A, "NIFTY", "SIG-1")).isEmpty();
    }

    @Test
    void findActivePosition_picksTheRequestedOptionSideOnly() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of(
                dto("NIFTY25SEP25000CE", 75, 0),
                dto("NIFTY25SEP25000PE", 260, 0)
        ));

        Optional<Position> ce = service.findActivePosition(USER_A, "NIFTY", OptionType.CE, "SIG-1");
        Optional<Position> pe = service.findActivePosition(USER_A, "NIFTY", OptionType.PE, "SIG-1");

        assertThat(ce).isPresent();
        assertThat(ce.get().getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");
        assertThat(ce.get().getNetQuantity()).isEqualTo(75);
        assertThat(pe).isPresent();
        assertThat(pe.get().getTradingSymbol()).isEqualTo("NIFTY25SEP25000PE");
        assertThat(pe.get().getNetQuantity()).isEqualTo(260);
    }

    @Test
    void findActivePosition_noMatchingSide_returnsEmpty() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of(dto("NIFTY25SEP25000CE", 75, 0)));

        assertThat(service.findActivePosition(USER_A, "NIFTY", OptionType.PE, "SIG-1")).isEmpty();
    }

    @Test
    void findActivePosition_ignoresFlatOrShortPositions() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of(dto("NIFTY25SEP25000PE", 0, 0)));

        assertThat(service.findActivePosition(USER_A, "NIFTY", OptionType.PE, "SIG-1")).isEmpty();
    }

    @Test
    void findActivePosition_ignoresOtherUnderlyings() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of(dto("BANKNIFTY25SEP50000PE", 30, 0)));

        assertThat(service.findActivePosition(USER_A, "NIFTY", OptionType.PE, "SIG-1")).isEmpty();
    }

    @Test
    void getPositions_userA_neverSeesUserBsPositions() {
        when(growwApiClient.getPositions(USER_A, "FNO")).thenReturn(List.of(dto("NIFTY25SEP25000CE", 75, 0)));
        when(growwApiClient.getPositions(USER_B, "FNO")).thenReturn(List.of(dto("NIFTY25SEP26000PE", 130, 0)));

        List<Position> positionsA = service.getPositions(USER_A);
        List<Position> positionsB = service.getPositions(USER_B);

        assertThat(positionsA).extracting(Position::getTradingSymbol).containsExactly("NIFTY25SEP25000CE");
        assertThat(positionsB).extracting(Position::getTradingSymbol).containsExactly("NIFTY25SEP26000PE");
    }
}
