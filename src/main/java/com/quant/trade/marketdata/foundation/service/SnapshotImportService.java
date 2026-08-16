package com.quant.trade.marketdata.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.dao.MarketCalendarMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBarWriteMapper;
import com.quant.trade.marketdata.foundation.dao.MdfImportBatchMapper;
import com.quant.trade.marketdata.foundation.dao.MdfIndustryMembershipMapper;
import com.quant.trade.marketdata.foundation.dao.MdfIndustryTaxonomyMapper;
import com.quant.trade.marketdata.foundation.dao.MdfUniverseSnapshotMapper;
import com.quant.trade.marketdata.foundation.importer.SnapshotFileParser;
import com.quant.trade.marketdata.foundation.model.MdfImportBatchDO;
import com.quant.trade.marketdata.foundation.model.MdfIndustryMembershipDO;
import com.quant.trade.marketdata.foundation.model.MdfIndustryTaxonomyDO;
import com.quant.trade.marketdata.foundation.model.MdfUniverseSnapshotDO;
import com.quant.trade.marketdata.foundation.vo.ImportBatchVO;
import com.quant.trade.marketdata.manager.StockBasicRegistrationManager;
import com.quant.trade.marketdata.model.MarketCalendarDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 无凭据快照文件导入通道（契约 AC-04，ADR-0015 §2）。纯编排：
 * 文件解析/行级校验在 {@link SnapshotFileParser} 实现（file-protocol 职责，服务层不感知文件格式），
 * 本服务负责哈希幂等、证券登记、批量 ODKU 落库与批次记录。
 *
 * - 幂等三层：同 kind+file_hash 直接返回既有批次（不重复处理）；文件内重复唯一键计 skipped；
 *   落库 ODKU（已存在行刷新）。单位口径=元/股/小数（schema 冻结，不做万元换算猜测）。
 * - 导入数据 data_source=IMPORT_*，不冒充线上 Provider；不生成任何合成行情。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotImportService {

    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int WRITE_BATCH = 500;
    private static final int BAR_SELECT_LIMIT = 1_000;
    private static final String ADJUST_NONE = "NONE";

    private final MdfImportBatchMapper importBatchMapper;
    private final MdfUniverseSnapshotMapper universeMapper;
    private final MdfIndustryTaxonomyMapper taxonomyMapper;
    private final MdfIndustryMembershipMapper membershipMapper;
    private final MdfBarWriteMapper barWriteMapper;
    private final com.quant.trade.marketdata.dao.MarketCalendarMapper marketCalendarMapper;
    private final com.quant.trade.marketdata.foundation.dao.MdfDatasetMapper datasetMapper;
    private final com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper versionMapper;
    private final com.quant.trade.marketdata.dao.StockDailyBarMapper stockDailyBarMapper;
    private final VersionLineageService lineageService;
    private final StockBasicRegistrationManager registrationManager;
    private final TransactionTemplate txRequiresNew;
    private final ObjectMapper objectMapper;
    private final Clock marketDataClock;
    private final SnapshotFileParser parser;

    public MdfImportBatchDO importSnapshot(String importKind, Long datasetVersionId, String fileName, byte[] content) {
        String kind = importKind == null ? "" : importKind.trim();
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCodeEnum.CSV_EMPTY_FILE, "导入文件为空");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.CSV_FILE_TOO_LARGE, "导入文件超过大小限制");
        }
        String fileHash = sha256(content);
        MdfImportBatchDO existing = importBatchMapper.selectByKindAndHash(kind, fileHash);
        if (existing != null) {
            // 同内容重复导入：幂等返回既有批次，不重复处理（契约 AC-04）。
            return existing;
        }
        // R1 §七：版本绑定校验（DAILY_BAR 必绑导入类版本；其余 kind 可选仅留批次血缘）
        VersionBinding binding = resolveBinding(kind, datasetVersionId);
        // R1 §六/七：批次先建档取得 id（manifest 血缘来源），处理完成后回填计数
        MdfImportBatchDO batch = MdfImportBatchDO.builder()
                .importKind(kind).providerCode(providerCodeOf(kind)).fileName(safeFileName(fileName))
                .fileHash(fileHash).insertedCount(0).updatedCount(0).skippedCount(0).rejectedCount(0)
                .status("COMPLETED").datasetVersionId(binding.versionId())
                .build();
        txRequiresNew.executeWithoutResult(status -> {
            try {
                importBatchMapper.insert(batch);
            } catch (Exception duplicate) {
                MdfImportBatchDO raced = importBatchMapper.selectByKindAndHash(kind, fileHash);
                if (raced != null) {
                    batch.setId(raced.getId());
                } else {
                    throw duplicate;
                }
            }
        });
        if (batch.getId() == null || batch.getId() == 0) {
            return batch;
        }
        SnapshotFileParser.ParsedRows<?> parsed = switch (kind) {
            case FoundationConstants.IMPORT_KIND_UNIVERSE -> importUniverse(content);
            case FoundationConstants.IMPORT_KIND_CALENDAR -> importCalendar(content);
            case FoundationConstants.IMPORT_KIND_DAILY_BAR -> importDailyBar(content, binding, batch.getId());
            case FoundationConstants.IMPORT_KIND_TAXONOMY -> importTaxonomy(content);
            case FoundationConstants.IMPORT_KIND_MEMBERSHIP_PIT -> importMembershipPit(content);
            default -> throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_IMPORT_KIND_INVALID,
                    "导入类型不合法: " + kind);
        };
        importBatchMapper.updateCounts(batch.getId(), parsed.rows().size(), 0,
                parsed.skipped(), parsed.rejected(), toErrorReportJson(parsed.errors()));
        log.info("导入完成: kind={}, versionId={}, inserted={}, skipped={}, rejected={}",
                kind, binding.versionId(), parsed.rows().size(), parsed.skipped(), parsed.rejected());
        return importBatchMapper.selectById(batch.getId());
    }

    /** R1 §七：DAILY_BAR 必须绑定导入类版本且口径一致；其他 kind 传入时仅校验存在并留血缘。 */
    private VersionBinding resolveBinding(String kind, Long datasetVersionId) {
        if (FoundationConstants.IMPORT_KIND_DAILY_BAR.equals(kind)) {
            if (datasetVersionId == null) {
                throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                        "DAILY_BAR 导入必须提供 datasetVersionId（导入类数据集版本）");
            }
            var version = versionMapper.selectById(datasetVersionId);
            if (version == null) {
                throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_VERSION_NOT_FOUND, "目标版本不存在");
            }
            var dataset = datasetMapper.selectById(version.getDatasetId());
            if (dataset == null || !FoundationConstants.IMPORT_SOURCE_DAILY_BAR.equals(dataset.getProviderCode())) {
                throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_CONFLICT,
                        "目标版本必须属于导入类日 K 数据集（provider=IMPORT_CSV_DAILY）");
            }
            if (!"NONE".equals(dataset.getAdjustType()) || !"CN".equals(dataset.getMarketCode())) {
                throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_CONFLICT,
                        "目标版本口径不一致（首期仅 CN/NONE）");
            }
            return new VersionBinding(datasetVersionId, dataset.getProviderCode(), dataset.getAdjustType());
        }
        if (datasetVersionId != null && versionMapper.selectById(datasetVersionId) == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_VERSION_NOT_FOUND, "目标版本不存在");
        }
        return new VersionBinding(datasetVersionId, null, null);
    }

    private record VersionBinding(Long versionId, String expectedProvider, String expectedAdjust) {
    }

    public MdfImportBatchDO getBatch(long id) {
        MdfImportBatchDO batch = importBatchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "导入批次不存在");
        }
        return batch;
    }

    public List<MdfImportBatchDO> listBatches(String kind, int page, int pageSize) {
        return importBatchMapper.selectList(kind, Math.max(0, (page - 1) * pageSize), pageSize);
    }

    /** VO 装配（controller 不接触持久化模型）。 */
    public ImportBatchVO toImportVO(MdfImportBatchDO batch) {
        return ImportBatchVO.builder()
                .id(batch.getId()).importKind(batch.getImportKind()).providerCode(batch.getProviderCode())
                .fileName(batch.getFileName()).fileHash(batch.getFileHash())
                .insertedCount(batch.getInsertedCount()).updatedCount(batch.getUpdatedCount())
                .skippedCount(batch.getSkippedCount()).rejectedCount(batch.getRejectedCount())
                .status(batch.getStatus()).errorReportJson(batch.getErrorReportJson())
                .datasetVersionId(batch.getDatasetVersionId())
                .createdAt(batch.getCreatedAt())
                .build();
    }

    // ---------------------------------------------------------------- 各类导入（解析→登记→落库）

    private SnapshotFileParser.ParsedRows<MdfUniverseSnapshotDO> importUniverse(byte[] content) {
        SnapshotFileParser.ParsedRows<SnapshotFileParser.UniverseRow> parsed = parser.parseUniverse(content);
        LocalDateTime fetchedAt = now();
        List<MdfUniverseSnapshotDO> rows = parsed.rows().stream()
                .map(row -> MdfUniverseSnapshotDO.builder()
                        .providerCode(FoundationConstants.IMPORT_SOURCE_UNIVERSE)
                        .canonicalSymbol(row.canonicalSymbol()).symbol(row.canonicalSymbol())
                        .name(row.name()).market(row.market())
                        .totalMarketCap(row.totalMarketCap())
                        .circulatingMarketCap(row.circulatingMarketCap())
                        .turnoverRate(row.turnoverRate()).asOfDate(row.asOfDate()).fetchedAt(fetchedAt)
                        .build())
                .toList();
        registrationManager.ensureRegistered(rows.stream()
                .map(MdfUniverseSnapshotDO::getCanonicalSymbol).distinct().toList());
        txRequiresNew.executeWithoutResult(status -> batchWrite(rows.size(),
                (from, to) -> universeMapper.upsertBatch(rows.subList(from, to))));
        return rewrap(parsed, rows);
    }

    private SnapshotFileParser.ParsedRows<String[]> importCalendar(byte[] content) {
        SnapshotFileParser.ParsedRows<SnapshotFileParser.CalendarRow> parsed = parser.parseCalendar(content);
        List<String[]> rows = parsed.rows().stream()
                .map(row -> new String[]{row.marketCode(), row.tradeDate().toString(),
                        Boolean.toString(row.tradingDay())})
                .toList();
        txRequiresNew.executeWithoutResult(status -> upsertCalendar(rows));
        return rewrap(parsed, rows);
    }

    private SnapshotFileParser.ParsedRows<StockDailyBarDO> importDailyBar(byte[] content, VersionBinding binding,
                                                                          long batchId) {
        SnapshotFileParser.ParsedRows<SnapshotFileParser.DailyBarRow> parsed = parser.parseDailyBar(content);
        // R1 §七：市场口径校验（目标版本 CN → 证券前缀须为 SH/SZ/BJ），不一致整批拒绝
        for (SnapshotFileParser.DailyBarRow row : parsed.rows()) {
            String prefix = row.canonicalSymbol().substring(0, 2);
            if (!"SH".equals(prefix) && !"SZ".equals(prefix) && !"BJ".equals(prefix)) {
                throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_CONFLICT,
                        "证券市场与目标版本不一致: " + row.canonicalSymbol());
            }
        }
        LocalDateTime fetchedAt = now();
        List<StockDailyBarDO> rows = parsed.rows().stream()
                .map(row -> StockDailyBarDO.builder()
                        .canonicalSymbol(row.canonicalSymbol()).tradeDate(row.tradeDate())
                        .adjustType(ADJUST_NONE).dataSource(FoundationConstants.IMPORT_SOURCE_DAILY_BAR)
                        .openPrice(row.open()).highPrice(row.high()).lowPrice(row.low()).closePrice(row.close())
                        .volume(row.volumeShares()).amount(row.amountYuan()).fetchedAt(fetchedAt)
                        .build())
                .toList();
        registrationManager.ensureRegistered(rows.stream()
                .map(StockDailyBarDO::getCanonicalSymbol).distinct().toList());
        txRequiresNew.executeWithoutResult(status -> batchWrite(rows.size(),
                (from, to) -> barWriteMapper.upsertBatch(rows.subList(from, to))));
        // R1 §六/七：导入行归属版本 manifest（血缘=IMPORT_BATCH+批次 id）；按 symbol 各自 min/max 日期回读
        List<StockDailyBarDO> persisted = new ArrayList<>();
        java.util.Map<String, java.util.List<StockDailyBarDO>> bySymbol = new java.util.LinkedHashMap<>();
        rows.forEach(bar -> bySymbol.computeIfAbsent(bar.getCanonicalSymbol(), k -> new ArrayList<>()).add(bar));
        for (java.util.Map.Entry<String, java.util.List<StockDailyBarDO>> entry : bySymbol.entrySet()) {
            LocalDate symbolMin = entry.getValue().stream().map(StockDailyBarDO::getTradeDate)
                    .min(LocalDate::compareTo).orElseThrow();
            LocalDate symbolMax = entry.getValue().stream().map(StockDailyBarDO::getTradeDate)
                    .max(LocalDate::compareTo).orElseThrow();
            persisted.addAll(stockDailyBarMapper.selectByFilter(entry.getKey(), symbolMin, symbolMax,
                    ADJUST_NONE, FoundationConstants.IMPORT_SOURCE_DAILY_BAR, BAR_SELECT_LIMIT, 0));
        }
        lineageService.recordBars(binding.versionId(), persisted,
                FoundationConstants.LINEAGE_SOURCE_IMPORT, batchId);
        return rewrap(parsed, rows);
    }

    private SnapshotFileParser.ParsedRows<MdfIndustryTaxonomyDO> importTaxonomy(byte[] content) {
        SnapshotFileParser.ParsedRows<SnapshotFileParser.TaxonomyRow> parsed = parser.parseTaxonomy(content);
        List<MdfIndustryTaxonomyDO> rows = parsed.rows().stream()
                .map(row -> MdfIndustryTaxonomyDO.builder()
                        .taxonomyCode(row.taxonomyCode()).taxonomyName(row.taxonomyName())
                        .providerCode(row.providerCode()).isMutuallyExclusive(1).note(row.note())
                        .build())
                .toList();
        txRequiresNew.executeWithoutResult(status -> rows.forEach(taxonomyMapper::upsert));
        return rewrap(parsed, rows);
    }

    private SnapshotFileParser.ParsedRows<MdfIndustryMembershipDO> importMembershipPit(byte[] content) {
        SnapshotFileParser.ParsedRows<SnapshotFileParser.MembershipRow> parsed = parser.parseMembershipPit(content);
        LocalDateTime fetchedAt = now();
        List<MdfIndustryMembershipDO> rows = parsed.rows().stream()
                .map(row -> MdfIndustryMembershipDO.builder()
                        .taxonomyCode(row.taxonomyCode()).industryCode(row.industryCode())
                        .industryName(row.industryName()).canonicalSymbol(row.canonicalSymbol())
                        .effectiveFrom(row.effectiveFrom()).effectiveTo(row.effectiveTo())
                        .sourceProvider(FoundationConstants.IMPORT_SOURCE_MEMBERSHIP).fetchedAt(fetchedAt)
                        .build())
                .toList();
        registrationManager.ensureRegistered(rows.stream()
                .map(MdfIndustryMembershipDO::getCanonicalSymbol).distinct().toList());
        txRequiresNew.executeWithoutResult(status -> batchWrite(rows.size(),
                (from, to) -> membershipMapper.upsertBatch(rows.subList(from, to))));
        return rewrap(parsed, rows);
    }

    // ---------------------------------------------------------------- 落库辅助

    private interface BatchCall {
        void write(int from, int to);
    }

    private static void batchWrite(int size, BatchCall call) {
        for (int from = 0; from < size; from += WRITE_BATCH) {
            call.write(from, Math.min(from + WRITE_BATCH, size));
        }
    }

    private void upsertCalendar(List<String[]> rows) {
        for (String[] row : rows) {
            // market_calendar 幂等：命中唯一键走 UPDATE，未命中 INSERT。
            String market = row[0];
            LocalDate tradeDate = LocalDate.parse(row[1]);
            boolean tradingDay = Boolean.parseBoolean(row[2]);
            int updated = marketCalendarMapper.updateTradingDay(market, tradeDate, tradingDay);
            if (updated == 0) {
                marketCalendarMapper.insert(MarketCalendarDO.builder()
                        .marketCode(market).tradeDate(tradeDate)
                        .isTradingDay(tradingDay).isHalfDay(false).build());
            }
        }
    }

    private <T, R> SnapshotFileParser.ParsedRows<R> rewrap(SnapshotFileParser.ParsedRows<T> parsed, List<R> rows) {
        return new SnapshotFileParser.ParsedRows<>(rows, parsed.skipped(), parsed.rejected(), parsed.errors());
    }

    private String toErrorReportJson(List<Map<String, Object>> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private static String providerCodeOf(String kind) {
        return switch (kind) {
            case FoundationConstants.IMPORT_KIND_UNIVERSE -> FoundationConstants.IMPORT_SOURCE_UNIVERSE;
            case FoundationConstants.IMPORT_KIND_CALENDAR -> FoundationConstants.IMPORT_SOURCE_CALENDAR;
            case FoundationConstants.IMPORT_KIND_DAILY_BAR -> FoundationConstants.IMPORT_SOURCE_DAILY_BAR;
            case FoundationConstants.IMPORT_KIND_TAXONOMY -> FoundationConstants.IMPORT_SOURCE_TAXONOMY;
            case FoundationConstants.IMPORT_KIND_MEMBERSHIP_PIT -> FoundationConstants.IMPORT_SOURCE_MEMBERSHIP;
            default -> "IMPORT_CSV";
        };
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unnamed.csv";
        }
        return fileName.length() <= 255 ? fileName : fileName.substring(0, 255);
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "file hash 计算失败");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(marketDataClock);
    }
}
