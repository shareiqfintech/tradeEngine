package com.example.trading.service;

import com.example.trading.enums.OptionType;
import com.example.trading.groww.GrowwApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Downloads and caches Groww's instrument master CSV
 * ({@code https://growwapi-assets.groww.in/instruments/instrument.csv}),
 * which is the only place lot sizes, strikes, expiries, and exact trading
 * symbols for F&O contracts come from - nothing here is hardcoded per
 * underlying.
 *
 * <p>The file covers every listed instrument (CASH + FNO, NSE + BSE), so it
 * is downloaded once and column indices are resolved from the header row
 * (robust to Groww re-ordering columns) rather than hardcoded positions.
 */
@Slf4j
@Service
public class InstrumentMasterService {

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(12);
    private static final List<DateTimeFormatter> EXPIRY_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH));

    private final GrowwApiClient growwApiClient;
    private final AtomicReference<List<InstrumentRow>> cache = new AtomicReference<>(List.of());
    private final AtomicReference<Instant> lastRefreshedAt = new AtomicReference<>();
    private final Object refreshLock = new Object();

    public InstrumentMasterService(GrowwApiClient growwApiClient) {
        this.growwApiClient = growwApiClient;
    }

    /** Options (CE/PE, segment FNO) for one underlying, e.g. "NIFTY". */
    public List<InstrumentRow> getOptionsForUnderlying(String underlying) {
        ensureFresh();
        String target = underlying.trim().toUpperCase(Locale.ROOT);
        List<InstrumentRow> result = new ArrayList<>();
        for (InstrumentRow row : cache.get()) {
            if (row.underlyingSymbol() != null
                    && row.underlyingSymbol().equalsIgnoreCase(target)
                    && "FNO".equalsIgnoreCase(row.segment())
                    && row.instrumentType() != null) {
                result.add(row);
            }
        }
        return result;
    }

    /** Distinct underlyings that actually have FNO option rows right now - never a hardcoded list. */
    public List<String> listAvailableUnderlyings() {
        ensureFresh();
        return cache.get().stream()
                .map(InstrumentRow::underlyingSymbol)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    public void refreshNow() {
        synchronized (refreshLock) {
            String csv = growwApiClient.fetchInstrumentMasterCsv();
            List<InstrumentRow> parsed = parse(csv);
            cache.set(parsed);
            lastRefreshedAt.set(Instant.now());
            log.info("INSTRUMENT_MASTER_REFRESHED rows={}", parsed.size());
        }
    }

    private void ensureFresh() {
        Instant last = lastRefreshedAt.get();
        if (last == null || Duration.between(last, Instant.now()).compareTo(REFRESH_INTERVAL) > 0) {
            refreshNow();
        }
    }

    private List<InstrumentRow> parse(String csv) {
        List<InstrumentRow> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.split("\\r?\\n");
        if (lines.length == 0) {
            return rows;
        }

        Map<String, Integer> columnIndex = headerIndex(lines[0]);
        Integer tradingSymbolIdx = columnIndex.get("trading_symbol");
        Integer underlyingIdx = columnIndex.get("underlying_symbol");
        Integer expiryIdx = columnIndex.get("expiry_date");
        Integer strikeIdx = columnIndex.get("strike_price");
        Integer instrumentTypeIdx = columnIndex.get("instrument_type");
        Integer lotSizeIdx = columnIndex.get("lot_size");
        Integer segmentIdx = columnIndex.get("segment");
        Integer exchangeIdx = columnIndex.get("exchange");

        if (tradingSymbolIdx == null || underlyingIdx == null || expiryIdx == null
                || strikeIdx == null || instrumentTypeIdx == null || lotSizeIdx == null
                || segmentIdx == null || exchangeIdx == null) {
            log.error("INSTRUMENT_MASTER_HEADER_MISSING_COLUMNS header={}", lines[0]);
            return rows;
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(",");
            if (fields.length <= Math.max(Math.max(tradingSymbolIdx, underlyingIdx),
                    Math.max(Math.max(expiryIdx, strikeIdx), Math.max(instrumentTypeIdx, lotSizeIdx)))) {
                continue;
            }
            try {
                String segment = safe(fields, segmentIdx);
                if (!"FNO".equalsIgnoreCase(segment)) {
                    continue;
                }
                String instrumentTypeRaw = safe(fields, instrumentTypeIdx);
                OptionType optionType = "CE".equalsIgnoreCase(instrumentTypeRaw) ? OptionType.CE
                        : "PE".equalsIgnoreCase(instrumentTypeRaw) ? OptionType.PE
                        : null;

                LocalDate expiry = parseExpiry(safe(fields, expiryIdx));
                if (optionType == null || expiry == null) {
                    continue; // futures rows and unparsable rows are irrelevant to option resolution
                }

                rows.add(new InstrumentRow(
                        safe(fields, tradingSymbolIdx),
                        safe(fields, underlyingIdx),
                        expiry,
                        new BigDecimal(safe(fields, strikeIdx)),
                        optionType,
                        Integer.parseInt(safe(fields, lotSizeIdx)),
                        segment,
                        safe(fields, exchangeIdx)));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                log.debug("INSTRUMENT_MASTER_ROW_SKIPPED line={} reason={}", i, ex.getMessage());
            }
        }
        return rows;
    }

    private Map<String, Integer> headerIndex(String headerLine) {
        String[] columns = headerLine.split(",");
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < columns.length; i++) {
            index.put(columns[i].trim().toLowerCase(Locale.ROOT), i);
        }
        return index;
    }

    private String safe(String[] fields, int idx) {
        return idx < fields.length ? fields[idx].trim() : "";
    }

    private LocalDate parseExpiry(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter format : EXPIRY_FORMATS) {
            try {
                return LocalDate.parse(raw, format);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    /** One relevant (FNO option) row from the instrument master. */
    public record InstrumentRow(String tradingSymbol, String underlyingSymbol, LocalDate expiryDate,
                                 BigDecimal strikePrice, OptionType instrumentType, int lotSize,
                                 String segment, String exchange) {
    }
}
