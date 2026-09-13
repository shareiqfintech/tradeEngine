package com.example.trading.service;

import com.example.trading.config.TradingProperties;
import com.example.trading.dto.FnoResolutionParams;
import com.example.trading.dto.FnoTradeConfigRequest;
import com.example.trading.dto.FnoTradeConfigResponse;
import com.example.trading.dto.OptionContract;
import com.example.trading.entity.FnoTradeConfigEntity;
import com.example.trading.exception.InvalidTargetPointsException;
import com.example.trading.repository.FnoTradeConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Owns the PER-USER, per-underlying F&O trade override (expiry/strike/
 * option-type selection, lots, optional explicit lot size, and the
 * automatic profit-target {@code targetPoints}) that
 * {@code TradingEngineService} reads for every BUY signal.
 *
 * <p>The backend stays the sole authority: a user-selected lot size is
 * re-validated against {@link OptionContractResolver} (Groww's real
 * instrument master) on every save, and Target Points is validated against
 * {@code trading.exit.target.min-points/max-points} - nothing here trusts
 * the frontend, and nothing hardcodes a lot size for any underlying.
 */
@Slf4j
@Service
public class FnoTradeConfigService {

    private final FnoTradeConfigRepository repository;
    private final OptionContractResolver optionContractResolver;
    private final LiveQuoteService liveQuoteService;
    private final TradingProperties properties;

    public FnoTradeConfigService(FnoTradeConfigRepository repository,
                                  OptionContractResolver optionContractResolver,
                                  LiveQuoteService liveQuoteService,
                                  TradingProperties properties) {
        this.repository = repository;
        this.optionContractResolver = optionContractResolver;
        this.liveQuoteService = liveQuoteService;
        this.properties = properties;
    }

    /**
     * The resolution parameters {@code TradingEngineService} should use for
     * a BUY signal on this underlying, for this user - the saved override if
     * one exists, otherwise the server-config defaults. Hot path (per
     * signal, per user): no live-quote lookups.
     */
    public FnoResolutionParams getEffectiveParams(Long userId, String underlying) {
        return findConfig(userId, underlying)
                .map(this::toParams)
                .orElseGet(this::defaultParams);
    }

    /** The EFFECTIVE Target Points for this user/underlying - the saved value, else {@code trading.exit.target.default-points}. */
    public BigDecimal getEffectiveTargetPoints(Long userId, String underlying) {
        return findConfig(userId, underlying)
                .map(FnoTradeConfigEntity::getTargetPoints)
                .filter(points -> points != null && points.signum() > 0)
                .orElseGet(() -> properties.getExit().getTarget().getDefaultPoints());
    }

    /** Whether the automatic target exit is enabled for this user/underlying (defaults to enabled when no row exists). */
    public boolean isTargetEnabled(Long userId, String underlying) {
        return findConfig(userId, underlying)
                .map(FnoTradeConfigEntity::isTargetEnabled)
                .orElse(true);
    }

    /** GET /api/trading/fno/config/{underlying} - the calling user's effective config + a live resolved-contract preview. */
    public FnoTradeConfigResponse getConfig(Long userId, String underlying) {
        String normalized = normalize(underlying);
        Optional<FnoTradeConfigEntity> existing = findConfig(userId, normalized);
        FnoResolutionParams params = existing.map(this::toParams).orElseGet(this::defaultParams);
        BigDecimal spotPrice = liveQuoteService.getSpotPrice(userId, normalized);
        return buildResponse(normalized, params, spotPrice,
                existing.isPresent() && existing.get().getLotSize() != null,
                effectiveTargetPoints(existing),
                existing.map(FnoTradeConfigEntity::isTargetEnabled).orElse(true));
    }

    /**
     * PUT /api/trading/fno/config/{underlying} - validates the requested
     * settings (lot size against the CURRENT resolved contract, Target
     * Points against the configured range) then persists this user's
     * override. An invalid value never gets saved.
     */
    @Transactional
    public FnoTradeConfigResponse updateConfig(Long userId, String underlying, FnoTradeConfigRequest request) {
        String normalized = normalize(underlying);

        BigDecimal targetPoints = request.getTargetPoints();
        if (targetPoints != null) {
            validateTargetPoints(targetPoints);
        }

        FnoResolutionParams params = new FnoResolutionParams(
                request.getOptionType(), request.getExpirySelection(), request.getStrikeSelection(),
                request.getStrikeOffset(), request.getLots(), request.getLotSize());

        BigDecimal spotPrice = liveQuoteService.getSpotPrice(userId, normalized);

        FnoTradeConfigEntity entity = findConfig(userId, normalized).orElseGet(FnoTradeConfigEntity::new);
        boolean targetEnabled = request.getTargetEnabled() != null
                ? request.getTargetEnabled()
                : (entity.getId() != null ? entity.isTargetEnabled() : true);

        // Resolving throws InvalidLotSizeException/ContractNotFoundException if anything about
        // this request doesn't hold up against Groww's real instrument master right now.
        FnoTradeConfigResponse response = buildResponse(normalized, params, spotPrice,
                request.getLotSize() != null,
                targetPoints != null ? targetPoints : properties.getExit().getTarget().getDefaultPoints(),
                targetEnabled);

        entity.setUserId(userId);
        entity.setUnderlying(normalized);
        entity.setOptionType(request.getOptionType());
        entity.setStrikeSelection(request.getStrikeSelection());
        entity.setStrikeOffset(request.getStrikeOffset());
        entity.setExpirySelection(request.getExpirySelection());
        entity.setLots(request.getLots());
        entity.setLotSize(request.getLotSize());
        entity.setTargetPoints(targetPoints); // null = "use the server default"
        entity.setTargetEnabled(targetEnabled);
        repository.save(entity);

        log.info("FNO_TRADE_CONFIG_UPDATED userId={} underlying={} optionType={} expirySelection={} strikeSelection={} strikeOffset={} lots={} lotSize={} targetPoints={} targetEnabled={}",
                userId, normalized, request.getOptionType(), request.getExpirySelection(), request.getStrikeSelection(),
                request.getStrikeOffset(), request.getLots(), request.getLotSize(), targetPoints, targetEnabled);

        return response;
    }

    private void validateTargetPoints(BigDecimal targetPoints) {
        BigDecimal min = properties.getExit().getTarget().getMinPoints();
        BigDecimal max = properties.getExit().getTarget().getMaxPoints();
        if (targetPoints.signum() <= 0 || targetPoints.compareTo(min) < 0 || targetPoints.compareTo(max) > 0) {
            throw InvalidTargetPointsException.outOfRange(targetPoints, min, max);
        }
    }

    private BigDecimal effectiveTargetPoints(Optional<FnoTradeConfigEntity> existing) {
        return existing.map(FnoTradeConfigEntity::getTargetPoints)
                .filter(points -> points != null && points.signum() > 0)
                .orElseGet(() -> properties.getExit().getTarget().getDefaultPoints());
    }

    private FnoTradeConfigResponse buildResponse(String underlying, FnoResolutionParams params, BigDecimal spotPrice,
                                                   boolean userSuppliedLotSize, BigDecimal targetPoints, boolean targetEnabled) {
        OptionContract contract = optionContractResolver.resolve(underlying, spotPrice, params);
        List<Integer> validLotSizes = optionContractResolver.validLotSizesFor(underlying, spotPrice, params);

        return FnoTradeConfigResponse.builder()
                .underlying(underlying)
                .optionType(params.optionType())
                .strikeSelection(params.strikeSelection())
                .strikeOffset(params.strikeOffset())
                .expirySelection(params.expirySelection())
                .lots(params.lots())
                .lotSize(contract.getLotSize())
                .lotSizeSource(userSuppliedLotSize
                        ? FnoTradeConfigResponse.LotSizeSource.USER
                        : FnoTradeConfigResponse.LotSizeSource.DEFAULT)
                .validLotSizes(validLotSizes)
                .quantity(contract.getLotSize() * params.lots())
                .resolvedContract(contract)
                .targetPoints(targetPoints)
                .targetEnabled(targetEnabled)
                .targetPointsMin(properties.getExit().getTarget().getMinPoints())
                .targetPointsMax(properties.getExit().getTarget().getMaxPoints())
                .build();
    }

    private Optional<FnoTradeConfigEntity> findConfig(Long userId, String underlying) {
        return repository.findByUserIdAndUnderlying(userId, normalize(underlying));
    }

    private FnoResolutionParams toParams(FnoTradeConfigEntity entity) {
        return new FnoResolutionParams(entity.getOptionType(), entity.getExpirySelection(), entity.getStrikeSelection(),
                entity.getStrikeOffset(), entity.getLots(), entity.getLotSize());
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

    private String normalize(String underlying) {
        return underlying.trim().toUpperCase(Locale.ROOT);
    }
}
