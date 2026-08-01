package com.quant.trade.marketdata.provider.csv;

import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.enums.SecurityListStatusEnum;
import com.quant.trade.marketdata.enums.SecurityTypeEnum;
import com.quant.trade.marketdata.enums.StockAliasTypeEnum;
import com.quant.trade.marketdata.exception.SecurityDirectoryImportException;
import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import com.quant.trade.marketdata.util.SecurityTextNormalizer;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportErrorVO;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportResultVO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 证券目录 CSV 解析器（D3 路径 P2）。
 * <p>
 * 复用 D1 冻结口径：同一 REQUIRED/OPTIONAL 表头、同一 alias 格式（{@code ALIAS_TYPE:LANGUAGE:VALUE}）、
 * 同一 enum 域、同一 RFC-3339 source_updated_at / ISO list_date、同一 duplicate/conflict 检测。
 * 与 D1 {@code SecurityDirectoryService.importCsv} 对同一输入产出相同的 stock/alias 候选集合与失败
 * reasonCode（等价性由参数化测试 {@code SecurityDirectoryCsvParserEquivalenceTest} 证明）。
 * 该解析器只解析与校验，不做持久化。
 */
public class SecurityDirectoryCsvParser {

    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    public static final int MAX_ROWS = 200_000;
    private static final int MAX_ERRORS = 50;
    private static final int MAX_ERROR_MESSAGE = 240;
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "canonical_symbol", "name", "market", "exchange", "currency", "security_type",
            "list_status", "data_source", "source_updated_at");
    private static final Set<String> OPTIONAL_HEADERS = Set.of(
            "name_cn", "name_hk", "name_en", "short_name", "pinyin_full", "pinyin_abbr",
            "list_date", "source_hash", "aliases");
    private static final Set<String> ALL_HEADERS;
    private static final Set<String> MARKETS = Set.of("SH", "SZ", "BJ", "HK", "US");
    private static final Set<String> SECURITY_TYPES = Arrays.stream(SecurityTypeEnum.values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> LIST_STATUSES = Arrays.stream(SecurityListStatusEnum.values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());

    static {
        LinkedHashSet<String> all = new LinkedHashSet<>(REQUIRED_HEADERS);
        all.addAll(OPTIONAL_HEADERS);
        ALL_HEADERS = Set.copyOf(all);
    }

    /** 解析并校验后的目录批次。 */
    public record ParsedDirectoryBatch(long totalRows, long duplicateUnchanged,
                                       List<DirectoryRow> rows) {
    }

    /** 单行解析结果。 */
    public record DirectoryRow(long line, StockBasicDO stock, List<StockAliasDO> aliases) {
    }

    /**
     * 解析并校验 CSV 字节流。任一 header/行级非法抛 {@link SecurityDirectoryImportException}，
     * 携带 bounded line/field/reasonCode/message（与 D1 一致）。
     */
    public ParsedDirectoryBatch parse(byte[] bytes) {
        validateSize(bytes);
        String decoded = decode(bytes);
        List<SecurityDirectoryImportErrorVO> errors = new ArrayList<>();
        LinkedHashMap<String, DirectoryRow> uniqueRows = new LinkedHashMap<>();
        long totalRows = 0;
        long duplicateUnchanged = 0;
        Set<Long> failedLines = new LinkedHashSet<>();
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(decoded.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.RFC4180.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setAllowDuplicateHeaderNames(true)
                     .build()
                     .parse(reader)) {
            validateHeaders(parser.getHeaderNames());
            for (CSVRecord record : parser) {
                totalRows++;
                long line = record.getRecordNumber() + 1;
                if (totalRows > MAX_ROWS) {
                    addError(errors, line, "file", "TOO_MANY_ROWS", "CSV 数据行超过 200000 行限制");
                    failedLines.add(line);
                    break;
                }
                if (!record.isConsistent()) {
                    addError(errors, line, "row", "MALFORMED_CSV", "CSV 列数与表头不一致");
                    failedLines.add(line);
                    continue;
                }
                try {
                    DirectoryRow row = parseRow(record, line, parser.getHeaderMap().keySet());
                    DirectoryRow first = uniqueRows.get(row.stock().getCanonicalSymbol());
                    if (first == null) {
                        uniqueRows.put(row.stock().getCanonicalSymbol(), row);
                    } else if (sameRow(first, row)) {
                        duplicateUnchanged++;
                    } else if (sameDirectoryData(first.stock(), row.stock())
                            && hasAliasMetadataConflict(first.aliases(), row.aliases())) {
                        addError(errors, first.line(), "aliases", "CONFLICTING_ALIAS_METADATA",
                                "与第 " + line + " 行的同一 alias identity 元数据冲突");
                        addError(errors, line, "aliases", "CONFLICTING_ALIAS_METADATA",
                                "与第 " + first.line() + " 行的同一 alias identity 元数据冲突");
                        failedLines.add(first.line());
                        failedLines.add(line);
                    } else {
                        addError(errors, first.line(), "canonical_symbol", "CONFLICTING_DUPLICATE",
                                "与第 " + line + " 行的同一证券内容冲突");
                        addError(errors, line, "canonical_symbol", "CONFLICTING_DUPLICATE",
                                "与第 " + first.line() + " 行的同一证券内容冲突");
                        failedLines.add(first.line());
                        failedLines.add(line);
                    }
                } catch (RowValidationException exception) {
                    addError(errors, line, exception.field(), exception.reasonCode(), exception.getMessage());
                    failedLines.add(line);
                }
            }
        } catch (SecurityDirectoryImportException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException | UncheckedIOException exception) {
            throw importFailure(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR, 0, "file", "MALFORMED_CSV",
                    "CSV 格式不合法");
        }
        if (!errors.isEmpty()) {
            throw validationFailure(totalRows, failedLines.size(), errors);
        }
        return new ParsedDirectoryBatch(totalRows, duplicateUnchanged, List.copyOf(uniqueRows.values()));
    }

    private void validateSize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw importFailure(ErrorCodeEnum.CSV_EMPTY_FILE, 0, "file", "EMPTY_FILE", "CSV 文件为空");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw importFailure(ErrorCodeEnum.CSV_FILE_TOO_LARGE, 0, "file", "FILE_TOO_LARGE",
                    "CSV 文件超过 50 MiB 限制");
        }
    }

    private String decode(byte[] bytes) {
        int offset = bytes.length >= 3
                && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException exception) {
            throw importFailure(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR, 0, "file", "MALFORMED_UTF8",
                    "CSV 必须是严格 UTF-8 编码");
        }
    }

    private void validateHeaders(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            throw importFailure(ErrorCodeEnum.CSV_WRONG_HEADER, 1, "header", "MISSING_HEADER", "CSV 表头缺失");
        }
        Set<String> actual = new LinkedHashSet<>();
        for (String header : headers) {
            String normalized = header == null ? "" : header.trim();
            if (!actual.add(normalized)) {
                throw importFailure(ErrorCodeEnum.CSV_WRONG_HEADER, 1, normalized, "DUPLICATE_HEADER",
                        "CSV 表头存在重复列");
            }
            if (!ALL_HEADERS.contains(normalized)) {
                throw importFailure(ErrorCodeEnum.CSV_WRONG_HEADER, 1, normalized, "UNKNOWN_HEADER",
                        "CSV 包含未知表头");
            }
        }
        List<String> missing = REQUIRED_HEADERS.stream().filter(header -> !actual.contains(header)).sorted().toList();
        if (!missing.isEmpty()) {
            throw importFailure(ErrorCodeEnum.CSV_WRONG_HEADER, 1, "header", "MISSING_REQUIRED_HEADER",
                    "CSV 缺少必需表头: " + String.join(",", missing));
        }
    }

    private DirectoryRow parseRow(CSVRecord record, long line, Set<String> headers) {
        String rawCanonical = required(record, "canonical_symbol");
        String market = enumValue(record, "market", MARKETS);
        String canonical;
        try {
            canonical = CanonicalSymbolUtils.normalize(rawCanonical);
        } catch (IllegalArgumentException exception) {
            throw rowError("canonical_symbol", "INVALID_SYMBOL", "canonical_symbol 格式不合法");
        }
        if (!canonical.startsWith(market + ".")) {
            throw rowError("market", "MARKET_MISMATCH", "market 与 canonical_symbol 不一致");
        }
        String name = required(record, "name");
        String securityType = enumValue(record, "security_type", SECURITY_TYPES);
        String listStatus = enumValue(record, "list_status", LIST_STATUSES);
        LocalDate listDate = optionalDate(optional(record, headers, "list_date"), "list_date");
        LocalDateTime sourceUpdatedAt = requiredTimestamp(required(record, "source_updated_at"));
        StockBasicDO stock = StockBasicDO.builder()
                .canonicalSymbol(canonical)
                .symbol(canonical.substring(canonical.indexOf('.') + 1))
                .name(cleanDisplay(name))
                .market(market)
                .nameCn(cleanOptional(optional(record, headers, "name_cn")))
                .nameHk(cleanOptional(optional(record, headers, "name_hk")))
                .nameEn(cleanOptional(optional(record, headers, "name_en")))
                .shortName(cleanOptional(optional(record, headers, "short_name")))
                .pinyinFull(cleanOptional(optional(record, headers, "pinyin_full")))
                .pinyinAbbr(cleanOptional(optional(record, headers, "pinyin_abbr")))
                .exchange(required(record, "exchange"))
                .currency(required(record, "currency").toUpperCase(Locale.ROOT))
                .securityType(securityType)
                .listStatus(listStatus)
                .dataSource(required(record, "data_source"))
                .sourceUpdatedAt(sourceUpdatedAt)
                .sourceHash(cleanOptional(optional(record, headers, "source_hash")))
                .listDate(listDate)
                .delisted(SecurityListStatusEnum.DELISTED.name().equals(listStatus))
                .build();
        List<StockAliasDO> aliases = parseAliases(optional(record, headers, "aliases"), stock.getDataSource());
        return new DirectoryRow(line, stock, aliases);
    }

    private List<StockAliasDO> parseAliases(String value, String dataSource) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        LinkedHashMap<String, StockAliasDO> aliases = new LinkedHashMap<>();
        for (String entry : value.split("\\|", -1)) {
            if (entry.isEmpty()) {
                throw rowError("aliases", "EMPTY_ALIAS_ENTRY", "aliases 包含空条目");
            }
            int firstColon = entry.indexOf(':');
            int secondColon = firstColon < 0 ? -1 : entry.indexOf(':', firstColon + 1);
            if (firstColon <= 0 || secondColon < 0) {
                throw rowError("aliases", "INVALID_ALIAS_FORMAT",
                        "aliases 条目必须为 ALIAS_TYPE:LANGUAGE:VALUE");
            }
            String type = entry.substring(0, firstColon).trim().toUpperCase(Locale.ROOT);
            try {
                StockAliasTypeEnum.valueOf(type);
            } catch (IllegalArgumentException exception) {
                throw rowError("aliases", "INVALID_ALIAS_TYPE", "aliases 包含未知 alias_type");
            }
            String language = cleanOptional(entry.substring(firstColon + 1, secondColon));
            String aliasValue = cleanDisplay(entry.substring(secondColon + 1));
            if (aliasValue.isBlank()) {
                throw rowError("aliases", "BLANK_ALIAS_VALUE", "alias value 不能为空");
            }
            String normalized = SecurityTextNormalizer.normalize(aliasValue);
            String key = type + "|" + normalized;
            StockAliasDO incoming = StockAliasDO.builder()
                    .alias(aliasValue)
                    .normalizedAlias(normalized)
                    .normalizedAliasKey(SecurityTextNormalizer.identityKey(normalized))
                    .aliasType(type)
                    .language(language)
                    .dataSource(dataSource)
                    .build();
            StockAliasDO existing = aliases.putIfAbsent(key, incoming);
            if (existing != null && !sameAliasMetadata(existing, incoming)) {
                throw rowError("aliases", "CONFLICTING_ALIAS_METADATA",
                        "同一 alias identity 的 language 或 display metadata 冲突");
            }
        }
        return List.copyOf(aliases.values());
    }

    private boolean sameRow(DirectoryRow first, DirectoryRow second) {
        if (!sameDirectoryData(first.stock(), second.stock())) {
            return false;
        }
        return aliasMetadataByIdentity(first.aliases()).equals(aliasMetadataByIdentity(second.aliases()));
    }

    private boolean hasAliasMetadataConflict(List<StockAliasDO> first, List<StockAliasDO> second) {
        java.util.Map<String, String> firstMetadata = aliasMetadataByIdentity(first);
        java.util.Map<String, String> secondMetadata = aliasMetadataByIdentity(second);
        return firstMetadata.entrySet().stream().anyMatch(entry ->
                secondMetadata.containsKey(entry.getKey())
                        && !Objects.equals(entry.getValue(), secondMetadata.get(entry.getKey())));
    }

    private java.util.Map<String, String> aliasMetadataByIdentity(List<StockAliasDO> aliases) {
        return aliases.stream().collect(Collectors.toMap(
                this::aliasKey,
                this::aliasMetadataKey,
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private String aliasKey(StockAliasDO alias) {
        return alias.getAliasType() + "|" + alias.getNormalizedAlias();
    }

    private String aliasMetadataKey(StockAliasDO alias) {
        return String.join("\u0000",
                Objects.toString(alias.getAlias(), ""),
                Objects.toString(alias.getNormalizedAlias(), ""),
                Objects.toString(alias.getAliasType(), ""),
                Objects.toString(alias.getLanguage(), ""),
                Objects.toString(alias.getDataSource(), ""),
                Objects.toString(alias.getEffectiveFrom(), ""),
                Objects.toString(alias.getEffectiveTo(), ""));
    }

    private boolean sameAliasMetadata(StockAliasDO left, StockAliasDO right) {
        return aliasMetadataKey(left).equals(aliasMetadataKey(right));
    }

    public static boolean sameDirectoryData(StockBasicDO left, StockBasicDO right) {
        return com.quant.trade.marketdata.util.SecurityDirectoryIdentityCalculator.sameDirectoryData(left, right);
    }

    private String required(CSVRecord record, String field) {
        String value = record.get(field);
        if (value == null || value.isBlank()) {
            throw rowError(field, "BLANK_REQUIRED_VALUE", field + " 不能为空");
        }
        return cleanDisplay(value);
    }

    private String enumValue(CSVRecord record, String field, Set<String> allowed) {
        String value = required(record, field).toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw rowError(field, "INVALID_ENUM", field + " 枚举值不合法");
        }
        return value;
    }

    private String optional(CSVRecord record, Set<String> headers, String field) {
        return headers.contains(field) ? record.get(field) : null;
    }

    private LocalDate optionalDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw rowError(field, "INVALID_DATE", field + " 必须为 yyyy-MM-dd");
        }
    }

    private LocalDateTime requiredTimestamp(String value) {
        try {
            return LocalDateTime.ofInstant(
                    OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            throw rowError("source_updated_at", "INVALID_TIMESTAMP",
                    "source_updated_at 必须为带 offset 的 RFC-3339 时间");
        }
    }

    private String cleanDisplay(String value) {
        return Normalizer.normalize(Objects.requireNonNullElse(value, ""), Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ");
    }

    private String cleanOptional(String value) {
        String cleaned = cleanDisplay(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private RowValidationException rowError(String field, String reasonCode, String message) {
        return new RowValidationException(field, reasonCode, sanitize(message));
    }

    private void addError(List<SecurityDirectoryImportErrorVO> errors, long line, String field,
                          String reasonCode, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(new SecurityDirectoryImportErrorVO(line, field, reasonCode, sanitize(message)));
        }
    }

    private SecurityDirectoryImportException validationFailure(
            long totalRows, long failedRows, List<SecurityDirectoryImportErrorVO> errors) {
        SecurityDirectoryImportResultVO result = new SecurityDirectoryImportResultVO(
                totalRows, 0, 0, 0, 0, 0, 0, failedRows, List.copyOf(errors));
        SecurityDirectoryImportErrorVO first = errors.get(0);
        return new SecurityDirectoryImportException(ErrorCodeEnum.DAILY_BAR_VALIDATION_ERROR,
                "line=" + first.line() + ", field=" + first.field() + ", reason=" + first.reasonCode()
                        + ": " + first.message(), result);
    }

    private SecurityDirectoryImportException importFailure(
            ErrorCodeEnum code, long line, String field, String reasonCode, String message) {
        SecurityDirectoryImportErrorVO error = new SecurityDirectoryImportErrorVO(
                line, field, reasonCode, sanitize(message));
        SecurityDirectoryImportResultVO result = new SecurityDirectoryImportResultVO(
                0, 0, 0, 0, 0, 0, 0, 1, List.of(error));
        return new SecurityDirectoryImportException(code,
                "line=" + line + ", field=" + field + ", reason=" + reasonCode + ": " + error.message(), result);
    }

    private String sanitize(String message) {
        String safe = Objects.requireNonNullElse(message, "输入不合法")
                .replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return safe.length() <= MAX_ERROR_MESSAGE ? safe : safe.substring(0, MAX_ERROR_MESSAGE);
    }

    private static final class RowValidationException extends RuntimeException {
        private final String field;
        private final String reasonCode;

        private RowValidationException(String field, String reasonCode, String message) {
            super(message);
            this.field = field;
            this.reasonCode = reasonCode;
        }

        private String field() { return field; }
        private String reasonCode() { return reasonCode; }
    }
}
