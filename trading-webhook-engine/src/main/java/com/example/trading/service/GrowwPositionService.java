package com.example.trading.service;

import com.example.trading.dto.Position;
import com.example.trading.entity.PositionSnapshotEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.enums.OptionType;
import com.example.trading.groww.dto.GrowwPositionDto;
import com.example.trading.groww.GrowwApiClient;
import com.example.trading.repository.PositionSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Queries Groww's real positions API - never trusts only the local
 * database - and maps the wire format to the clean internal {@link Position}
 * model. Every query is also persisted to {@code position_snapshot} so there
 * is a point-in-time audit trail of what the broker reported when a SELL
 * decision was made.
 */
@Slf4j
@Service
public class GrowwPositionService {

    private static final String SEGMENT_FNO = "FNO";

    private final GrowwApiClient growwApiClient;
    private final PositionSnapshotRepository snapshotRepository;
    private final AuditService auditService;

    public GrowwPositionService(GrowwApiClient growwApiClient,
                                 PositionSnapshotRepository snapshotRepository,
                                 AuditService auditService) {
        this.growwApiClient = growwApiClient;
        this.snapshotRepository = snapshotRepository;
        this.auditService = auditService;
    }

    /** All current F&O positions at the broker for {@code userId}, freshly queried using that user's own access token. */
    public List<Position> getPositions(Long userId) {
        List<GrowwPositionDto> raw = growwApiClient.getPositions(userId, SEGMENT_FNO);
        List<Position> positions = raw.stream().map(this::toInternal).toList();
        persistSnapshots(positions);
        return positions;
    }

    /**
     * Finds an open LONG position on {@code underlying} (matched by the
     * Groww trading symbol starting with the underlying's name, e.g.
     * "NIFTY25SEP25000CE".startsWith("NIFTY")), for the SELL flow.
     */
    public Optional<Position> findLongPosition(Long userId, String underlying, String signalId) {
        List<Position> positions = getPositions(userId);
        String prefix = underlying.trim().toUpperCase(Locale.ROOT);

        Optional<Position> match = positions.stream()
                .filter(p -> p.getTradingSymbol() != null && p.getTradingSymbol().toUpperCase(Locale.ROOT).startsWith(prefix))
                .filter(Position::isLong)
                .findFirst();

        auditService.record(AuditEventType.POSITION_CHECK, signalId, null,
                "underlying=" + underlying + " longPositionFound=" + match.isPresent()
                        + (match.map(p -> " symbol=" + p.getTradingSymbol() + " netQty=" + p.getNetQuantity()).orElse("")));
        return match;
    }

    /**
     * Finds an open LONG position on {@code underlying} for a SPECIFIC
     * option side (CE or PE) - used by the signal-driven position-switching
     * flow to find exactly the opposite-side position that must be closed
     * before a new same-direction contract is bought. CE/PE is determined
     * from Groww's own trading-symbol suffix convention (e.g.
     * "NIFTY2691523500PE") since {@link GrowwPositionDto} carries no
     * separate instrument-type field.
     */
    public Optional<Position> findActivePosition(Long userId, String underlying, OptionType optionType, String signalId) {
        List<Position> positions = getPositions(userId);
        String prefix = underlying.trim().toUpperCase(Locale.ROOT);
        String suffix = optionType.name();

        Optional<Position> match = positions.stream()
                .filter(p -> p.getTradingSymbol() != null)
                .filter(p -> p.getTradingSymbol().toUpperCase(Locale.ROOT).startsWith(prefix))
                .filter(p -> p.getTradingSymbol().toUpperCase(Locale.ROOT).endsWith(suffix))
                .filter(Position::isLong)
                .findFirst();

        auditService.record(AuditEventType.POSITION_CHECK, signalId, null,
                "underlying=" + underlying + " optionType=" + optionType + " activePositionFound=" + match.isPresent()
                        + (match.map(p -> " symbol=" + p.getTradingSymbol() + " netQty=" + p.getNetQuantity()).orElse("")));
        return match;
    }

    private Position toInternal(GrowwPositionDto dto) {
        int quantity = orZero(dto.getQuantity());
        int carryForward = orZero(dto.getNetCarryForwardQuantity());
        return Position.builder()
                .tradingSymbol(dto.getTradingSymbol())
                .exchange(dto.getExchange())
                .segment(SEGMENT_FNO)
                .quantity(quantity)
                .averagePrice(dto.getNetPrice())
                .product(dto.getProduct())
                .netQuantity(quantity + carryForward)
                .build();
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void persistSnapshots(List<Position> positions) {
        for (Position position : positions) {
            PositionSnapshotEntity entity = new PositionSnapshotEntity();
            entity.setTradingSymbol(position.getTradingSymbol());
            entity.setExchange(position.getExchange());
            entity.setSegment(position.getSegment());
            entity.setQuantity(position.getQuantity());
            entity.setNetQuantity(position.getNetQuantity());
            entity.setAveragePrice(position.getAveragePrice());
            entity.setProduct(position.getProduct());
            snapshotRepository.save(entity);
        }
    }
}
