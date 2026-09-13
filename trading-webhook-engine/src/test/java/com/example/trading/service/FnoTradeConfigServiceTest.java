package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.FnoResolutionParams;
import com.example.trading.dto.FnoTradeConfigRequest;
import com.example.trading.dto.FnoTradeConfigResponse;
import com.example.trading.dto.OptionContract;
import com.example.trading.entity.FnoTradeConfigEntity;
import com.example.trading.enums.OptionType;
import com.example.trading.exception.InvalidLotSizeException;
import com.example.trading.exception.InvalidTargetPointsException;
import com.example.trading.repository.FnoTradeConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FnoTradeConfigServiceTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    private FnoTradeConfigRepository repository;
    private OptionContractResolver optionContractResolver;
    private LiveQuoteService liveQuoteService;
    private TradingProperties properties;
    private FnoTradeConfigService service;

    private final OptionContract contract = OptionContract.builder()
            .tradingSymbol("NIFTY25SEP25000CE").lotSize(75).exchange("NSE").segment("FNO")
            .optionType(OptionType.CE).strike(BigDecimal.valueOf(25000)).expiry(LocalDate.now().plusDays(3))
            .buyAllowed(true).sellAllowed(true).build();

    @BeforeEach
    void setUp() {
        repository = mock(FnoTradeConfigRepository.class);
        optionContractResolver = mock(OptionContractResolver.class);
        liveQuoteService = mock(LiveQuoteService.class);
        properties = new TradingProperties();
        service = new FnoTradeConfigService(repository, optionContractResolver, liveQuoteService, properties);

        when(liveQuoteService.getSpotPrice(anyLong(), eq("NIFTY"))).thenReturn(BigDecimal.valueOf(25000));
        when(optionContractResolver.resolve(eq("NIFTY"), any(), any())).thenReturn(contract);
        when(optionContractResolver.validLotSizesFor(eq("NIFTY"), any(), any())).thenReturn(List.of(75));
    }

    private FnoTradeConfigRequest baseRequest() {
        FnoTradeConfigRequest request = new FnoTradeConfigRequest();
        request.setOptionType("CE");
        request.setExpirySelection("NEAREST");
        request.setStrikeSelection("ATM");
        request.setStrikeOffset(0);
        request.setLots(2);
        return request;
    }

    // ---- resolution params (existing behaviour, now user-scoped) ----

    @Test
    void getEffectiveParams_noSavedConfig_usesServerDefaults() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.empty());

        FnoResolutionParams params = service.getEffectiveParams(USER_A, "NIFTY");

        assertThat(params.optionType()).isEqualTo(properties.getOption().getOptionType());
        assertThat(params.lots()).isEqualTo(properties.getQuantity().getLots());
        assertThat(params.lotSize()).isNull();
    }

    @Test
    void getEffectiveParams_withSavedConfig_usesOverride() {
        FnoTradeConfigEntity entity = new FnoTradeConfigEntity();
        entity.setUserId(USER_A);
        entity.setUnderlying("NIFTY");
        entity.setOptionType("PE");
        entity.setExpirySelection("NEXT");
        entity.setStrikeSelection("OTM");
        entity.setStrikeOffset(2);
        entity.setLots(3);
        entity.setLotSize(65);
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.of(entity));

        FnoResolutionParams params = service.getEffectiveParams(USER_A, "NIFTY");

        assertThat(params.optionType()).isEqualTo("PE");
        assertThat(params.lots()).isEqualTo(3);
        assertThat(params.lotSize()).isEqualTo(65);
    }

    @Test
    void updateConfig_noLotSize_resolvesAndUsesContractDefault() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FnoTradeConfigResponse response = service.updateConfig(USER_A, "nifty", baseRequest());

        assertThat(response.getLotSize()).isEqualTo(75);
        assertThat(response.getLotSizeSource()).isEqualTo(FnoTradeConfigResponse.LotSizeSource.DEFAULT);
        assertThat(response.getQuantity()).isEqualTo(150); // 2 lots * 75
        assertThat(response.getResolvedContract().getTradingSymbol()).isEqualTo("NIFTY25SEP25000CE");

        ArgumentCaptor<FnoTradeConfigEntity> captor = ArgumentCaptor.forClass(FnoTradeConfigEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_A);
        assertThat(captor.getValue().getUnderlying()).isEqualTo("NIFTY");
        assertThat(captor.getValue().getLotSize()).isNull();
    }

    @Test
    void updateConfig_invalidLotSize_neverSaves() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.empty());
        when(optionContractResolver.resolve(eq("NIFTY"), any(), any()))
                .thenThrow(new InvalidLotSizeException(999, List.of(75)));

        FnoTradeConfigRequest request = baseRequest();
        request.setLotSize(999);

        assertThatThrownBy(() -> service.updateConfig(USER_A, "NIFTY", request))
                .isInstanceOf(InvalidLotSizeException.class);

        verify(repository, never()).save(any());
    }

    // ---- Target Points ----

    @Test
    void updateConfig_targetPoints_persistedAndEchoed() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FnoTradeConfigRequest request = baseRequest();
        request.setTargetPoints(new BigDecimal("12"));

        FnoTradeConfigResponse response = service.updateConfig(USER_A, "NIFTY", request);

        assertThat(response.getTargetPoints()).isEqualByComparingTo("12");
        assertThat(response.isTargetEnabled()).isTrue();
        ArgumentCaptor<FnoTradeConfigEntity> captor = ArgumentCaptor.forClass(FnoTradeConfigEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTargetPoints()).isEqualByComparingTo("12");
    }

    @Test
    void getEffectiveTargetPoints_noSavedValue_usesServerDefault() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.empty());

        assertThat(service.getEffectiveTargetPoints(USER_A, "NIFTY"))
                .isEqualByComparingTo(properties.getExit().getTarget().getDefaultPoints()); // 8
    }

    @Test
    void getEffectiveTargetPoints_savedValue_wins() {
        FnoTradeConfigEntity entity = savedEntity(USER_A, new BigDecimal("15"));
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.of(entity));

        assertThat(service.getEffectiveTargetPoints(USER_A, "NIFTY")).isEqualByComparingTo("15");
    }

    @Test
    void updateConfig_targetPointsZero_rejectedAndNeverSaved() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.empty());
        FnoTradeConfigRequest request = baseRequest();
        request.setTargetPoints(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.updateConfig(USER_A, "NIFTY", request))
                .isInstanceOf(InvalidTargetPointsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateConfig_targetPointsNegative_rejected() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.empty());
        FnoTradeConfigRequest request = baseRequest();
        request.setTargetPoints(new BigDecimal("-3"));

        assertThatThrownBy(() -> service.updateConfig(USER_A, "NIFTY", request))
                .isInstanceOf(InvalidTargetPointsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateConfig_targetPointsAboveMax_rejected() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY")).thenReturn(Optional.empty());
        FnoTradeConfigRequest request = baseRequest();
        request.setTargetPoints(properties.getExit().getTarget().getMaxPoints().add(BigDecimal.ONE));

        assertThatThrownBy(() -> service.updateConfig(USER_A, "NIFTY", request))
                .isInstanceOf(InvalidTargetPointsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void targetPoints_areIndependentPerUser() {
        when(repository.findByUserIdAndUnderlying(USER_A, "NIFTY"))
                .thenReturn(Optional.of(savedEntity(USER_A, new BigDecimal("8"))));
        when(repository.findByUserIdAndUnderlying(USER_B, "NIFTY"))
                .thenReturn(Optional.of(savedEntity(USER_B, new BigDecimal("12"))));

        assertThat(service.getEffectiveTargetPoints(USER_A, "NIFTY")).isEqualByComparingTo("8");
        assertThat(service.getEffectiveTargetPoints(USER_B, "NIFTY")).isEqualByComparingTo("12");
    }

    private FnoTradeConfigEntity savedEntity(Long userId, BigDecimal targetPoints) {
        FnoTradeConfigEntity entity = new FnoTradeConfigEntity();
        entity.setUserId(userId);
        entity.setUnderlying("NIFTY");
        entity.setOptionType("CE");
        entity.setExpirySelection("NEAREST");
        entity.setStrikeSelection("ATM");
        entity.setStrikeOffset(0);
        entity.setLots(2);
        entity.setTargetPoints(targetPoints);
        entity.setTargetEnabled(true);
        return entity;
    }
}
