package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dao.StockAliasMapper;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.enums.SecurityCatalogStatusEnum;
import com.quant.trade.marketdata.enums.SecurityListStatusEnum;
import com.quant.trade.marketdata.enums.SecurityMatchedByEnum;
import com.quant.trade.marketdata.enums.SecurityTypeEnum;
import com.quant.trade.marketdata.enums.StockAliasTypeEnum;
import com.quant.trade.marketdata.exception.SecurityDirectoryImportException;
import com.quant.trade.marketdata.exception.SecurityDirectoryNotFoundException;
import com.quant.trade.marketdata.model.SecuritySearchCandidateDO;
import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import com.quant.trade.marketdata.util.SecurityTextNormalizer;
import com.quant.trade.marketdata.vo.SecurityDetailVO;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportErrorVO;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportResultVO;
import com.quant.trade.marketdata.vo.SecuritySearchItemVO;
import com.quant.trade.marketdata.vo.SecuritySearchResultVO;
import com.quant.trade.marketdata.vo.StockAliasVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** D1 本地证券目录导入、确定性检索和详情服务。 */
@Service
@RequiredArgsConstructor
public class SecurityDirectoryService {

    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    public static final int MAX_ROWS = 200_000;
    private static final int MAX_ERRORS = 50;
    private static final int MAX_ERROR_MESSAGE = 240;
    private static final Duration STALE_AFTER = Duration.ofHours(48);
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "canonical_symbol", "name", "market", "exchange", "currency", "security_type",
            "list_status", "data_source", "source_updated_at");
    private static final Set<String> OPTIONAL_HEADERS = Set.of(
            "name_cn", "name_hk", "name_en", "short_name", "pinyin_full", "pinyin_abbr",
            "list_date", "source_hash", "aliases");
    private static final Set<String> ALL_HEADERS;

    static {
        LinkedHashSet<String> all = new LinkedHashSet<>(REQUIRED_HEADERS);
        all.addAll(OPTIONAL_HEADERS);
        ALL_HEADERS = Set.copyOf(all);
    }

    private final StockBasicMapper stockBasicMapper;
    private final StockAliasMapper stockAliasMapper;
    private final Clock marketDataClock;

    @Transactional
    public SecurityDirectoryImportResultVO importCsv(InputStream input, long declaredSize) {
        byte[] bytes = readAndValidateFile(input, declaredSize);
        ParsedBatch batch = parseAndValidate(bytes);
        try {
            return persist(batch);
        } catch (SecurityDirectoryImportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw importFailure(ErrorCodeEnum.DAILY_BAR_VALIDATION_ERROR, 0, "persistence",
                    "PERSISTENCE_FAILED", "目录写入失败，整批已回滚");
        }
    }

    public SecuritySearchResultVO search(String rawQuery, List<String> rawMarkets, List<String> rawTypes,
                                         boolean includeDelisted, int limit) {
        String query = validateAndNormalizeQuery(rawQuery);
        List<String> markets = normalizeMarkets(rawMarkets);
        List<String> types = normalizeTypes(rawTypes);
        if (limit < 1 || limit > 100) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "limit 必须在 1..100");
        }

        String queryUpper = query.toUpperCase(Locale.ROOT);
        String canonicalQuery = normalizeCanonicalQuery(queryUpper);
        String hkSymbol = normalizeHkNumericQuery(query);
        List<SecuritySearchCandidateDO> candidates = stockBasicMapper.searchCandidates(
                query, queryUpper, canonicalQuery, hkSymbol, markets, types, includeDelisted);
        Map<String, Integer> marketOrder = new HashMap<>();
        for (int index = 0; index < markets.size(); index++) {
            marketOrder.put(markets.get(index), index);
        }
        List<ScoredSecurity> scored = candidates.stream()
                .map(candidate -> score(candidate, query, queryUpper, canonicalQuery, hkSymbol))
                .filter(Objects::nonNull)
                .sorted(searchComparator(marketOrder))
                .limit(limit)
                .toList();
        CatalogMetadata metadata = catalogMetadata();
        return new SecuritySearchResultVO(
                scored.stream().map(ScoredSecurity::item).toList(),
                metadata.status(), metadata.updatedAt(), metadata.stale(), false);
    }

    public SecurityDetailVO detail(String rawCanonicalSymbol) {
        final String canonicalSymbol;
        try {
            canonicalSymbol = CanonicalSymbolUtils.normalize(rawCanonicalSymbol);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, sanitize(exception.getMessage()));
        }
        StockBasicDO stock = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
        if (stock == null) {
            throw new SecurityDirectoryNotFoundException(canonicalSymbol);
        }
        List<StockAliasVO> aliases = stockAliasMapper.selectByStockBasicId(stock.getId()).stream()
                .map(alias -> new StockAliasVO(alias.getAlias(), alias.getNormalizedAlias(), alias.getAliasType(),
                        alias.getLanguage(), alias.getDataSource(), alias.getEffectiveFrom(), alias.getEffectiveTo()))
                .toList();
        return new SecurityDetailVO(
                stock.getId(), stock.getCanonicalSymbol(), stock.getSymbol(), displayName(stock),
                stock.getName(), stock.getNameCn(), stock.getNameHk(), stock.getNameEn(), stock.getShortName(),
                stock.getPinyinFull(), stock.getPinyinAbbr(), stock.getMarket(), stock.getExchange(),
                stock.getCurrency(), stock.getSecurityType(), effectiveListStatus(stock),
                effectiveDelisted(stock), stock.getListDate(), stock.getDataSource(),
                toInstant(stock.getSourceUpdatedAt()), stock.getSourceHash(), aliases);
    }

    private byte[] readAndValidateFile(InputStream input, long declaredSize) {
        if (input == null || declaredSize == 0) {
            throw importFailure(ErrorCodeEnum.CSV_EMPTY_FILE, 0, "file", "EMPTY_FILE", "CSV 文件为空");
        }
        if (declaredSize < 0) {
            throw importFailure(ErrorCodeEnum.PARAM_ERROR, 0, "file", "INVALID_FILE_SIZE", "文件大小不合法");
        }
        if (declaredSize > MAX_FILE_SIZE) {
            throw importFailure(ErrorCodeEnum.CSV_FILE_TOO_LARGE, 0, "file", "FILE_TOO_LARGE",
                    "CSV 文件超过 50 MiB 限制");
        }
        try {
            byte[] bytes = input.readNBytes((int) MAX_FILE_SIZE + 1);
            if (bytes.length == 0) {
                throw importFailure(ErrorCodeEnum.CSV_EMPTY_FILE, 0, "file", "EMPTY_FILE", "CSV 文件为空");
            }
            if (bytes.length > MAX_FILE_SIZE) {
                throw importFailure(ErrorCodeEnum.CSV_FILE_TOO_LARGE, 0, "file", "FILE_TOO_LARGE",
                        "CSV 文件超过 50 MiB 限制");
            }
            return bytes;
        } catch (SecurityDirectoryImportException exception) {
            throw exception;
        } catch (IOException exception) {
            throw importFailure(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR, 0, "file", "READ_FAILED",
                    "CSV 文件读取失败");
        }
    }

    private ParsedBatch parseAndValidate(byte[] bytes) {
        String decoded;
        try {
            int offset = bytes.length >= 3
                    && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF ? 3 : 0;
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException exception) {
            throw importFailure(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR, 0, "file", "MALFORMED_UTF8",
                    "CSV 必须是严格 UTF-8 编码");
        }
        List<SecurityDirectoryImportErrorVO> errors = new ArrayList<>();
        LinkedHashMap<String, DirectoryRow> uniqueRows = new LinkedHashMap<>();
        long totalRows = 0;
        long duplicateUnchanged = 0;
        long failedRows = 0;
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
                    failedRows++;
                    break;
                }
                if (!record.isConsistent()) {
                    addError(errors, line, "row", "MALFORMED_CSV", "CSV 列数与表头不一致");
                    failedRows++;
                    continue;
                }
                try {
                    DirectoryRow row = parseRow(record, line, parser.getHeaderMap().keySet());
                    DirectoryRow first = uniqueRows.get(row.stock().getCanonicalSymbol());
                    if (first == null) {
                        uniqueRows.put(row.stock().getCanonicalSymbol(), row);
                    } else if (sameRow(first, row)) {
                        duplicateUnchanged++;
                    } else {
                        addError(errors, first.line(), "canonical_symbol", "CONFLICTING_DUPLICATE",
                                "与第 " + line + " 行的同一证券内容冲突");
                        addError(errors, line, "canonical_symbol", "CONFLICTING_DUPLICATE",
                                "与第 " + first.line() + " 行的同一证券内容冲突");
                        failedRows += 2;
                    }
                } catch (RowValidationException exception) {
                    addError(errors, line, exception.field(), exception.reasonCode(), exception.getMessage());
                    failedRows++;
                }
            }
        } catch (SecurityDirectoryImportException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException | UncheckedIOException exception) {
            throw importFailure(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR, 0, "file", "MALFORMED_CSV",
                    "CSV 格式不合法");
        }
        if (!errors.isEmpty()) {
            throw validationFailure(totalRows, failedRows, errors);
        }
        return new ParsedBatch(totalRows, duplicateUnchanged, List.copyOf(uniqueRows.values()));
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
        String market = enumValue(record, "market", Set.of("SH", "SZ", "BJ", "HK", "US"));
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
        String securityType = enumValue(record, "security_type",
                Arrays.stream(SecurityTypeEnum.values()).map(Enum::name).collect(Collectors.toSet()));
        String listStatus = enumValue(record, "list_status",
                Arrays.stream(SecurityListStatusEnum.values()).map(Enum::name).collect(Collectors.toSet()));
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
            aliases.putIfAbsent(key, StockAliasDO.builder()
                    .alias(aliasValue)
                    .normalizedAlias(normalized)
                    .aliasType(type)
                    .language(language)
                    .dataSource(dataSource)
                    .build());
        }
        return List.copyOf(aliases.values());
    }

    private SecurityDirectoryImportResultVO persist(ParsedBatch batch) {
        Map<String, StockBasicDO> existingStocks = preloadStocks(batch.rows());
        Map<Long, Set<String>> existingAliasKeys = preloadAliases(existingStocks.values());
        long inserted = 0;
        long updated = 0;
        long unchanged = batch.duplicateUnchanged();
        long aliasesInserted = 0;
        long aliasesUnchanged = 0;
        long formerNamesAdded = 0;
        for (DirectoryRow row : batch.rows()) {
            StockBasicDO incoming = row.stock();
            StockBasicDO existing = existingStocks.get(incoming.getCanonicalSymbol());
            if (existing == null) {
                stockBasicMapper.insertDirectory(incoming);
                existing = incoming;
                inserted++;
                existingAliasKeys.put(existing.getId(), new LinkedHashSet<>());
            } else if (sameDirectoryData(existing, incoming)) {
                unchanged++;
            } else {
                incoming.setId(existing.getId());
                if (existing.getName() != null && !existing.getName().isBlank()
                        && !Objects.equals(existing.getName(), incoming.getName())) {
                    StockAliasDO former = StockAliasDO.builder()
                            .stockBasicId(existing.getId())
                            .alias(cleanDisplay(existing.getName()))
                            .normalizedAlias(SecurityTextNormalizer.normalize(existing.getName()))
                            .aliasType(StockAliasTypeEnum.FORMER_NAME.name())
                            .dataSource(incoming.getDataSource())
                            .build();
                    if (insertAliasIfAbsent(former, existingAliasKeys)) {
                        aliasesInserted++;
                        formerNamesAdded++;
                    } else {
                        aliasesUnchanged++;
                    }
                }
                stockBasicMapper.updateDirectoryById(incoming);
                existing = incoming;
                updated++;
            }
            for (StockAliasDO alias : row.aliases()) {
                alias.setStockBasicId(existing.getId());
                if (insertAliasIfAbsent(alias, existingAliasKeys)) {
                    aliasesInserted++;
                } else {
                    aliasesUnchanged++;
                }
            }
        }
        return new SecurityDirectoryImportResultVO(batch.totalRows(), inserted, updated, unchanged,
                aliasesInserted, aliasesUnchanged, formerNamesAdded, 0, List.of());
    }

    private Map<String, StockBasicDO> preloadStocks(List<DirectoryRow> rows) {
        Map<String, StockBasicDO> result = new HashMap<>();
        List<String> symbols = rows.stream().map(row -> row.stock().getCanonicalSymbol()).toList();
        for (int start = 0; start < symbols.size(); start += 500) {
            List<String> part = symbols.subList(start, Math.min(start + 500, symbols.size()));
            stockBasicMapper.selectByCanonicalSymbols(part)
                    .forEach(stock -> result.put(stock.getCanonicalSymbol(), stock));
        }
        return result;
    }

    private Map<Long, Set<String>> preloadAliases(Iterable<StockBasicDO> stocks) {
        List<Long> ids = new ArrayList<>();
        stocks.forEach(stock -> ids.add(stock.getId()));
        Map<Long, Set<String>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new LinkedHashSet<>()));
        for (int start = 0; start < ids.size(); start += 500) {
            List<Long> part = ids.subList(start, Math.min(start + 500, ids.size()));
            stockAliasMapper.selectByStockBasicIds(part).forEach(alias ->
                    result.computeIfAbsent(alias.getStockBasicId(), ignored -> new LinkedHashSet<>())
                            .add(aliasKey(alias)));
        }
        return result;
    }

    private boolean insertAliasIfAbsent(StockAliasDO alias, Map<Long, Set<String>> existingAliasKeys) {
        Set<String> keys = existingAliasKeys.computeIfAbsent(alias.getStockBasicId(),
                ignored -> new LinkedHashSet<>());
        if (!keys.add(aliasKey(alias))) {
            return false;
        }
        stockAliasMapper.insert(alias);
        return true;
    }

    private String aliasKey(StockAliasDO alias) {
        return alias.getAliasType() + "|" + alias.getNormalizedAlias();
    }

    private ScoredSecurity score(SecuritySearchCandidateDO stock, String query, String queryUpper,
                                 String canonicalQuery, String hkSymbol) {
        SecurityMatchedByEnum matchedBy = null;
        int score = 0;
        if (stock.getCanonicalSymbol().equalsIgnoreCase(queryUpper)
                || Objects.equals(stock.getCanonicalSymbol(), canonicalQuery)) {
            matchedBy = SecurityMatchedByEnum.CANONICAL_SYMBOL_EXACT;
            score = 100;
        } else if (stock.getSymbol().equalsIgnoreCase(queryUpper)
                || ("HK".equals(stock.getMarket()) && Objects.equals(stock.getSymbol(), hkSymbol))) {
            matchedBy = SecurityMatchedByEnum.RAW_SYMBOL_EXACT;
            score = 95;
        } else if (formalNames(stock).stream().anyMatch(name -> name.equals(query))) {
            matchedBy = SecurityMatchedByEnum.FORMAL_NAME_EXACT;
            score = 90;
        } else if (formalNames(stock).stream().anyMatch(name -> name.startsWith(query))) {
            matchedBy = SecurityMatchedByEnum.FORMAL_NAME_PREFIX;
            score = 80;
        } else if (Boolean.TRUE.equals(stock.getAliasExact())) {
            matchedBy = SecurityMatchedByEnum.ALIAS_EXACT;
            score = 75;
        } else if (Boolean.TRUE.equals(stock.getAliasPrefix())) {
            matchedBy = SecurityMatchedByEnum.ALIAS_PREFIX;
            score = 75;
        } else if (startsWithNormalized(stock.getPinyinFull(), query)) {
            matchedBy = SecurityMatchedByEnum.PINYIN_FULL_PREFIX;
            score = 70;
        } else if (startsWithNormalized(stock.getPinyinAbbr(), query)) {
            matchedBy = SecurityMatchedByEnum.PINYIN_ABBR_PREFIX;
            score = 70;
        } else if (formalNames(stock).stream().anyMatch(name -> name.contains(query))) {
            matchedBy = SecurityMatchedByEnum.NAME_CONTAINS;
            score = 50;
        } else if (Boolean.TRUE.equals(stock.getAliasContains())) {
            matchedBy = SecurityMatchedByEnum.ALIAS_CONTAINS;
            score = 50;
        }
        if (matchedBy == null) {
            return null;
        }
        SecuritySearchItemVO item = new SecuritySearchItemVO(
                stock.getCanonicalSymbol(), stock.getSymbol(), displayName(stock), stock.getName(),
                stock.getNameCn(), stock.getNameHk(), stock.getNameEn(), stock.getShortName(),
                stock.getMarket(), stock.getExchange(), stock.getCurrency(), stock.getSecurityType(),
                effectiveListStatus(stock), matchedBy);
        return new ScoredSecurity(score, item, "LISTED".equals(effectiveListStatus(stock)),
                SecurityTextNormalizer.normalize(displayName(stock)));
    }

    private Comparator<ScoredSecurity> searchComparator(Map<String, Integer> marketOrder) {
        return Comparator.comparingInt(ScoredSecurity::score).reversed()
                .thenComparing(ScoredSecurity::listed, Comparator.reverseOrder())
                .thenComparingInt(scored -> marketOrder.isEmpty()
                        ? 0 : marketOrder.getOrDefault(scored.item().market(), Integer.MAX_VALUE))
                .thenComparing(ScoredSecurity::normalizedName)
                .thenComparing(scored -> scored.item().canonicalSymbol());
    }

    private List<String> formalNames(StockBasicDO stock) {
        return Arrays.asList(stock.getName(), stock.getNameCn(), stock.getNameHk(),
                        stock.getNameEn(), stock.getShortName()).stream()
                .filter(Objects::nonNull)
                .map(SecurityTextNormalizer::normalize)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private CatalogMetadata catalogMetadata() {
        if (stockBasicMapper.countAll() == 0) {
            return new CatalogMetadata(SecurityCatalogStatusEnum.EMPTY, null, false);
        }
        LocalDateTime max = stockBasicMapper.selectMaxSourceUpdatedAt();
        if (max == null) {
            return new CatalogMetadata(SecurityCatalogStatusEnum.READY, null, true);
        }
        Instant updatedAt = toInstant(max);
        boolean stale = marketDataClock.instant().isAfter(updatedAt.plus(STALE_AFTER));
        return new CatalogMetadata(SecurityCatalogStatusEnum.READY, updatedAt, stale);
    }

    private String validateAndNormalizeQuery(String rawQuery) {
        String query = SecurityTextNormalizer.normalize(rawQuery);
        if (query == null || query.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "q 不能为空");
        }
        boolean containsHan = query.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
        if (!containsHan && query.codePointCount(0, query.length()) < 2) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "拉丁字母或数字查询至少需要 2 个字符");
        }
        return query;
    }

    private List<String> normalizeMarkets(List<String> rawMarkets) {
        if (rawMarkets == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : flatten(rawMarkets)) {
            String market = value.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("SH", "SZ", "BJ", "HK", "US").contains(market)) {
                throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE, "无效 market: " + sanitize(market));
            }
            result.add(market);
        }
        return List.copyOf(result);
    }

    private List<String> normalizeTypes(List<String> rawTypes) {
        if (rawTypes == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : flatten(rawTypes)) {
            String type = value.trim().toUpperCase(Locale.ROOT);
            try {
                SecurityTypeEnum.valueOf(type);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE, "无效 security type: " + sanitize(type));
            }
            result.add(type);
        }
        return List.copyOf(result);
    }

    private List<String> flatten(List<String> values) {
        return values.stream().filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .filter(value -> !value.isBlank()).toList();
    }

    private String normalizeCanonicalQuery(String queryUpper) {
        if (!queryUpper.contains(".")) {
            return null;
        }
        try {
            return CanonicalSymbolUtils.normalize(queryUpper);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeHkNumericQuery(String query) {
        if (!query.matches("\\d{1,5}")) {
            return null;
        }
        try {
            return CanonicalSymbolUtils.normalize("HK." + query).substring(3);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean sameRow(DirectoryRow first, DirectoryRow second) {
        if (!sameDirectoryData(first.stock(), second.stock())) {
            return false;
        }
        return first.aliases().stream().map(this::aliasKey).collect(Collectors.toSet())
                .equals(second.aliases().stream().map(this::aliasKey).collect(Collectors.toSet()));
    }

    private boolean sameDirectoryData(StockBasicDO left, StockBasicDO right) {
        return Objects.equals(left.getCanonicalSymbol(), right.getCanonicalSymbol())
                && Objects.equals(left.getSymbol(), right.getSymbol())
                && Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getMarket(), right.getMarket())
                && Objects.equals(left.getNameCn(), right.getNameCn())
                && Objects.equals(left.getNameHk(), right.getNameHk())
                && Objects.equals(left.getNameEn(), right.getNameEn())
                && Objects.equals(left.getShortName(), right.getShortName())
                && Objects.equals(left.getPinyinFull(), right.getPinyinFull())
                && Objects.equals(left.getPinyinAbbr(), right.getPinyinAbbr())
                && Objects.equals(left.getExchange(), right.getExchange())
                && Objects.equals(left.getCurrency(), right.getCurrency())
                && Objects.equals(left.getSecurityType(), right.getSecurityType())
                && Objects.equals(left.getListStatus(), right.getListStatus())
                && Objects.equals(left.getDataSource(), right.getDataSource())
                && Objects.equals(left.getSourceUpdatedAt(), right.getSourceUpdatedAt())
                && Objects.equals(left.getSourceHash(), right.getSourceHash())
                && Objects.equals(left.getListDate(), right.getListDate())
                && Objects.equals(left.getDelisted(), right.getDelisted());
    }

    private boolean effectiveDelisted(StockBasicDO stock) {
        return Boolean.TRUE.equals(stock.getDelisted())
                || SecurityListStatusEnum.DELISTED.name().equals(stock.getListStatus());
    }

    private String effectiveListStatus(StockBasicDO stock) {
        return effectiveDelisted(stock) ? SecurityListStatusEnum.DELISTED.name()
                : stock.getListStatus() == null ? SecurityListStatusEnum.UNKNOWN.name() : stock.getListStatus();
    }

    private String displayName(StockBasicDO stock) {
        for (String value : List.of(
                Objects.requireNonNullElse(stock.getName(), ""),
                Objects.requireNonNullElse(stock.getNameCn(), ""),
                Objects.requireNonNullElse(stock.getNameHk(), ""),
                Objects.requireNonNullElse(stock.getNameEn(), ""),
                stock.getCanonicalSymbol())) {
            if (!value.isBlank()) {
                return value;
            }
        }
        return stock.getCanonicalSymbol();
    }

    private boolean startsWithNormalized(String value, String query) {
        String normalized = SecurityTextNormalizer.normalize(value);
        return normalized != null && normalized.startsWith(query);
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
            Instant instant = OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.SECONDS);
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
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

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
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

    private record DirectoryRow(long line, StockBasicDO stock, List<StockAliasDO> aliases) {
    }

    private record ParsedBatch(long totalRows, long duplicateUnchanged, List<DirectoryRow> rows) {
    }

    private record CatalogMetadata(SecurityCatalogStatusEnum status, Instant updatedAt, boolean stale) {
    }

    private record ScoredSecurity(int score, SecuritySearchItemVO item, boolean listed, String normalizedName) {
    }

    private static final class RowValidationException extends RuntimeException {
        private final String field;
        private final String reasonCode;

        private RowValidationException(String field, String reasonCode, String message) {
            super(message);
            this.field = field;
            this.reasonCode = reasonCode;
        }

        private String field() {
            return field;
        }

        private String reasonCode() {
            return reasonCode;
        }
    }
}
