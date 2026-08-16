package com.quant.trade.marketdata.foundation.importer;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据底座快照文件解析器（契约 AC-04；无 Mapper，只做解析与行级校验）。
 *
 * - 五类 schema 表头冻结；表头不匹配=整批 400（verifyHeader 显式比对首行文本，commons-csv 预设表头只是位置映射）。
 * - 行级错误收集进 errors（recordNumber/reason/raw，上限 50 条），合法行去重（文件内重复唯一键计 skipped）。
 * - 单位口径=元/股/小数；日期 YYYY-MM-DD；PIT 区间半开 [from,to)，to 空=至今，文件内重叠行拒绝。
 * - 行类型与结果结构见 {@link SnapshotFileParser}（接口隔离，服务层不感知文件格式细节）。
 */
@org.springframework.stereotype.Component
public final class CsvSnapshotParser implements SnapshotFileParser {

    public static final int MAX_ROWS = 200_000;
    private static final int MAX_ERRORS = 50;

    public static final String[] HEADER_UNIVERSE =
            {"symbol", "name", "market", "total_market_cap", "circulating_market_cap", "turnover_rate", "as_of_date"};
    public static final String[] HEADER_CALENDAR = {"market_code", "trade_date", "is_trading_day"};
    public static final String[] HEADER_DAILY_BAR =
            {"symbol", "trade_date", "open", "high", "low", "close", "volume", "amount"};
    public static final String[] HEADER_TAXONOMY = {"taxonomy_code", "taxonomy_name", "provider_code", "note"};
    public static final String[] HEADER_MEMBERSHIP =
            {"taxonomy_code", "industry_code", "industry_name", "symbol", "effective_from", "effective_to"};

    public ParsedRows<UniverseRow> parseUniverse(byte[] content) {
        List<CSVRecord> records = parse(content, HEADER_UNIVERSE);
        Map<String, UniverseRow> unique = new LinkedHashMap<>();
        Errors errors = new Errors();
        for (CSVRecord record : records) {
            try {
                String symbol = requireCanonical(record, "symbol");
                LocalDate asOf = LocalDate.parse(requireDate(record, "as_of_date"));
                String key = symbol + "|" + asOf;
                if (unique.containsKey(key)) {
                    errors.skipped++;
                    continue;
                }
                unique.put(key, new UniverseRow(symbol, optional(record, "name"), marketOf(symbol),
                        optionalDecimal(record, "total_market_cap"),
                        optionalDecimal(record, "circulating_market_cap"),
                        optionalDecimal(record, "turnover_rate"), asOf));
            } catch (IllegalArgumentException exception) {
                errors.reject(record, exception.getMessage());
            }
        }
        return new ParsedRows<>(new ArrayList<>(unique.values()), errors.skipped, errors.rejected, errors.list);
    }

    public ParsedRows<CalendarRow> parseCalendar(byte[] content) {
        List<CSVRecord> records = parse(content, HEADER_CALENDAR);
        Map<String, CalendarRow> unique = new LinkedHashMap<>();
        Errors errors = new Errors();
        for (CSVRecord record : records) {
            try {
                String market = require(record, "market_code").toUpperCase();
                if (!"CN".equals(market)) {
                    throw new IllegalArgumentException("首期仅支持 CN 日历");
                }
                LocalDate tradeDate = LocalDate.parse(requireDate(record, "trade_date"));
                boolean tradingDay = parseBoolean(require(record, "is_trading_day"), "is_trading_day");
                String key = market + "|" + tradeDate;
                if (unique.containsKey(key)) {
                    errors.skipped++;
                    continue;
                }
                unique.put(key, new CalendarRow(market, tradeDate, tradingDay));
            } catch (IllegalArgumentException exception) {
                errors.reject(record, exception.getMessage());
            }
        }
        return new ParsedRows<>(new ArrayList<>(unique.values()), errors.skipped, errors.rejected, errors.list);
    }

    public ParsedRows<DailyBarRow> parseDailyBar(byte[] content) {
        List<CSVRecord> records = parse(content, HEADER_DAILY_BAR);
        Map<String, DailyBarRow> unique = new LinkedHashMap<>();
        Errors errors = new Errors();
        for (CSVRecord record : records) {
            try {
                String symbol = requireCanonical(record, "symbol");
                LocalDate tradeDate = LocalDate.parse(requireDate(record, "trade_date"));
                String key = symbol + "|" + tradeDate;
                if (unique.containsKey(key)) {
                    errors.skipped++;
                    continue;
                }
                DailyBarRow row = new DailyBarRow(symbol, tradeDate,
                        requireDecimal(record, "open"), requireDecimal(record, "high"),
                        requireDecimal(record, "low"), requireDecimal(record, "close"),
                        requireLong(record, "volume"), requireDecimal(record, "amount"));
                validateOhlc(row);
                unique.put(key, row);
            } catch (IllegalArgumentException exception) {
                errors.reject(record, exception.getMessage());
            }
        }
        return new ParsedRows<>(new ArrayList<>(unique.values()), errors.skipped, errors.rejected, errors.list);
    }

    public ParsedRows<TaxonomyRow> parseTaxonomy(byte[] content) {
        List<CSVRecord> records = parse(content, HEADER_TAXONOMY);
        Map<String, TaxonomyRow> unique = new LinkedHashMap<>();
        Errors errors = new Errors();
        for (CSVRecord record : records) {
            try {
                String code = require(record, "taxonomy_code");
                if (unique.containsKey(code)) {
                    errors.skipped++;
                    continue;
                }
                unique.put(code, new TaxonomyRow(code, require(record, "taxonomy_name"),
                        require(record, "provider_code"), optional(record, "note")));
            } catch (IllegalArgumentException exception) {
                errors.reject(record, exception.getMessage());
            }
        }
        return new ParsedRows<>(new ArrayList<>(unique.values()), errors.skipped, errors.rejected, errors.list);
    }

    public ParsedRows<MembershipRow> parseMembershipPit(byte[] content) {
        List<CSVRecord> records = parse(content, HEADER_MEMBERSHIP);
        Map<String, MembershipRow> unique = new LinkedHashMap<>();
        Map<String, List<LocalDate[]>> acceptedPeriods = new HashMap<>();
        Errors errors = new Errors();
        for (CSVRecord record : records) {
            try {
                String taxonomy = require(record, "taxonomy_code");
                String industry = require(record, "industry_code");
                String symbol = requireCanonical(record, "symbol");
                LocalDate from = LocalDate.parse(requireDate(record, "effective_from"));
                String toText = optional(record, "effective_to");
                LocalDate to = toText == null ? null : LocalDate.parse(toText);
                if (to != null && !to.isAfter(from)) {
                    throw new IllegalArgumentException("effective_to 必须晚于 effective_from（半开区间）");
                }
                String key = taxonomy + "|" + symbol + "|" + from;
                if (unique.containsKey(key)) {
                    errors.skipped++;
                    continue;
                }
                if (overlaps(acceptedPeriods.get(symbol), from, to)) {
                    throw new IllegalArgumentException("与文件内既有区间重叠（同 symbol 半开区间不得交叉）");
                }
                acceptedPeriods.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(new LocalDate[]{from, to});
                unique.put(key, new MembershipRow(taxonomy, industry, require(record, "industry_name"),
                        symbol, from, to));
            } catch (IllegalArgumentException | DateTimeParseException exception) {
                errors.reject(record, exception.getMessage());
            }
        }
        return new ParsedRows<>(new ArrayList<>(unique.values()), errors.skipped, errors.rejected, errors.list);
    }

    // ---------------------------------------------------------------- 解析基础

    private List<CSVRecord> parse(byte[] content, String[] expectedHeader) {
        verifyHeader(content, expectedHeader);
        try {
            CSVFormat format = CSVFormat.RFC4180.builder()
                    .setHeader(expectedHeader)
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .build();
            try (CSVParser parser = CSVParser.parse(new ByteArrayInputStream(content), StandardCharsets.UTF_8, format)) {
                List<CSVRecord> records = parser.getRecords();
                if (records.size() > MAX_ROWS) {
                    throw new BusinessException(ErrorCodeEnum.CSV_TOO_MANY_ROWS, "导入行数超过限制 " + MAX_ROWS);
                }
                return records;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_IMPORT_FILE_INVALID,
                    "导入文件解析失败（表头必须为 " + String.join(",", expectedHeader) + "）");
        }
    }

    /** 表头文本校验：commons-csv 预设表头是位置映射，必须显式比对首行与冻结 schema。 */
    private void verifyHeader(byte[] content, String[] expectedHeader) {
        try (CSVParser parser = CSVParser.parse(new ByteArrayInputStream(content), StandardCharsets.UTF_8,
                CSVFormat.RFC4180.builder().setIgnoreEmptyLines(true).build())) {
            Iterator<CSVRecord> iterator = parser.iterator();
            if (!iterator.hasNext() || !Arrays.equals(iterator.next().values(), expectedHeader)) {
                throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_IMPORT_FILE_INVALID,
                        "导入文件表头不合法（必须为 " + String.join(",", expectedHeader) + "）");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_IMPORT_FILE_INVALID,
                    "导入文件解析失败（表头必须为 " + String.join(",", expectedHeader) + "）");
        }
    }

    private String require(CSVRecord record, String column) {
        String value = record.isSet(column) ? record.get(column) : null;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(column + " 不能为空");
        }
        return value.trim();
    }

    private String optional(CSVRecord record, String column) {
        String value = record.isSet(column) ? record.get(column) : null;
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireCanonical(CSVRecord record, String column) {
        return CanonicalSymbolUtils.normalize(require(record, column));
    }

    private String requireDate(CSVRecord record, String column) {
        String value = require(record, column);
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(column + " 日期格式必须为 YYYY-MM-DD: " + value);
        }
    }

    private BigDecimal requireDecimal(CSVRecord record, String column) {
        String value = require(record, column);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(column + " 必须为数值: " + value);
        }
    }

    private BigDecimal optionalDecimal(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(column + " 必须为数值: " + value);
        }
    }

    private Long requireLong(CSVRecord record, String column) {
        String value = require(record, column);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(column + " 必须为整数（股）: " + value);
        }
    }

    private boolean parseBoolean(String value, String column) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "Y".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "N".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(column + " 必须为 true/false");
    }

    private void validateOhlc(DailyBarRow bar) {
        if (bar.high().compareTo(bar.low()) < 0
                || bar.high().compareTo(bar.open()) < 0
                || bar.high().compareTo(bar.close()) < 0
                || bar.low().compareTo(bar.open()) > 0
                || bar.low().compareTo(bar.close()) > 0
                || bar.open().signum() <= 0) {
            throw new IllegalArgumentException("OHLC 不合法（high>=max(open,close,low) 且价格>0）");
        }
        if (bar.volumeShares() != null && bar.volumeShares() < 0) {
            throw new IllegalArgumentException("volume 不能为负");
        }
    }

    private static boolean overlaps(List<LocalDate[]> periods, LocalDate from, LocalDate to) {
        if (periods == null) {
            return false;
        }
        for (LocalDate[] period : periods) {
            LocalDate existingTo = period[1] == null ? LocalDate.MAX : period[1];
            LocalDate newTo = to == null ? LocalDate.MAX : to;
            if (from.isBefore(existingTo) && period[0].isBefore(newTo)) {
                return true;
            }
        }
        return false;
    }

    private static String marketOf(String canonicalSymbol) {
        return canonicalSymbol.substring(0, 2);
    }

    /** 错误/跳过累计器（错误上限 50 条）。 */
    private static final class Errors {
        private final List<Map<String, Object>> list = new ArrayList<>();
        private int skipped;
        private int rejected;

        private void reject(CSVRecord record, String reason) {
            rejected++;
            if (list.size() >= MAX_ERRORS) {
                return;
            }
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("recordNumber", record.getRecordNumber());
            error.put("reason", reason == null ? "未知错误" : reason);
            error.put("raw", String.join(",", record.values()));
            list.add(error);
        }
    }
}
