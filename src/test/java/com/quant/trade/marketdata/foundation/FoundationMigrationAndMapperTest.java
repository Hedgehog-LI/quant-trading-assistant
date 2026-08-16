package com.quant.trade.marketdata.foundation;

import com.quant.trade.marketdata.foundation.dao.MdfBackfillChunkMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.dao.MdfCoverageWatermarkMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.dao.MdfImportBatchMapper;
import com.quant.trade.marketdata.foundation.dao.MdfIndustryMembershipMapper;
import com.quant.trade.marketdata.foundation.dao.MdfIndustryTaxonomyMapper;
import com.quant.trade.marketdata.foundation.dao.MdfQualityResultMapper;
import com.quant.trade.marketdata.foundation.dao.MdfUniverseSnapshotMapper;
import com.quant.trade.marketdata.foundation.model.MdfBackfillChunkDO;
import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import com.quant.trade.marketdata.foundation.model.MdfCoverageWatermarkDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.model.MdfImportBatchDO;
import com.quant.trade.marketdata.foundation.model.MdfIndustryMembershipDO;
import com.quant.trade.marketdata.foundation.model.MdfIndustryTaxonomyDO;
import com.quant.trade.marketdata.foundation.model.MdfQualityResultDO;
import com.quant.trade.marketdata.foundation.model.MdfUniverseSnapshotDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T01/T02：空库 Flyway 迁移到 V24 + mdf_* 全表 MyBatis XML 实际读写。
 * 事实表复用断言：不存在 mdf_daily_bar 之类的日 K 复制表（D1）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FoundationMigrationAndMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MdfDatasetMapper datasetMapper;
    @Autowired
    private MdfDatasetVersionMapper versionMapper;
    @Autowired
    private MdfUniverseSnapshotMapper universeMapper;
    @Autowired
    private MdfIndustryTaxonomyMapper taxonomyMapper;
    @Autowired
    private MdfIndustryMembershipMapper membershipMapper;
    @Autowired
    private MdfCoverageWatermarkMapper coverageMapper;
    @Autowired
    private MdfBackfillTaskMapper taskMapper;
    @Autowired
    private MdfBackfillChunkMapper chunkMapper;
    @Autowired
    private MdfImportBatchMapper importBatchMapper;
    @Autowired
    private MdfQualityResultMapper qualityResultMapper;

    @Test
    void flywayMigratesEmptyDatabaseToV24() {
        Double maxApplied = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS DOUBLE)) FROM flyway_schema_history WHERE success = TRUE", Double.class);
        assertEquals(24.0, maxApplied, "空库必须迁移到 V24");
        // D1：日 K/证券/日历事实复用既有表，不得出现 mdf_ 复制表
        assertEquals(0L, countTable("mdf_daily_bar"), "不得复制 stock_daily_bar 事实（D1）");
        assertEquals(1L, countTable("mdf_dataset"), "V24 mdf_dataset 必须存在（同时证明 information_schema 检查有效）");
        assertEquals(1L, countTable("mdf_quality_result"));
    }

    private Long countTable(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_name = ?", Long.class, table);
    }

    @Test
    void datasetAndVersionRoundTrip() {
        MdfDatasetDO dataset = MdfDatasetDO.builder()
                .datasetCode("TEST_DS").datasetName("测试数据集").marketCode("CN")
                .barType("DAILY").frequency("1D").providerCode("TENCENT_PUBLIC").adjustType("NONE")
                .unitCaliber("元/股/元/小数").description("测试").build();
        datasetMapper.insert(dataset);
        assertNotNull(dataset.getId());
        assertEquals("TEST_DS", datasetMapper.selectByCode("TEST_DS").getDatasetCode());
        assertEquals(1, datasetMapper.selectAll().size());

        MdfDatasetVersionDO version = MdfDatasetVersionDO.builder()
                .datasetId(dataset.getId()).versionCode("v1").status("DRAFT")
                .startDate(LocalDate.of(2026, 7, 1)).endDate(LocalDate.of(2026, 7, 31))
                .sourceProvider("TENCENT_PUBLIC").rowCount(0L).build();
        versionMapper.insert(version);
        assertEquals(1, versionMapper.selectMaxVersionSeq(dataset.getId()), "version_code=v1 → 序号 1");
        MdfDatasetVersionDO second = MdfDatasetVersionDO.builder()
                .datasetId(dataset.getId()).versionCode("v2").status("DRAFT")
                .startDate(LocalDate.of(2026, 7, 1)).endDate(LocalDate.of(2026, 7, 31))
                .sourceProvider("TENCENT_PUBLIC").rowCount(0L).build();
        versionMapper.insert(second);
        assertEquals(2, versionMapper.selectMaxVersionSeq(dataset.getId()), "序号取 MAX 而非行数");
        MdfDatasetVersionDO v2 = versionMapper.selectById(version.getId());
        assertEquals("DRAFT", v2.getStatus());
        versionMapper.updateStatus(version.getId(), "QUALIFIED", LocalDateTime.of(2026, 8, 16, 10, 0), null, "note");
        versionMapper.updateRowCount(version.getId(), 100L);
        v2 = versionMapper.selectById(version.getId());
        assertEquals("QUALIFIED", v2.getStatus());
        assertEquals(100L, v2.getRowCount());
        assertEquals("note", v2.getSourceNote());
        datasetMapper.updateCurrentVersion(dataset.getId(), version.getId());
        assertEquals(version.getId(), datasetMapper.selectById(dataset.getId()).getCurrentVersionId());
    }

    @Test
    void universeSnapshotUpsertIsIdempotent() {
        MdfUniverseSnapshotDO row = MdfUniverseSnapshotDO.builder()
                .providerCode("IMPORT_CSV_UNIVERSE").canonicalSymbol("SH.600519").symbol("SH.600519")
                .name("贵州茅台").market("SH")
                .totalMarketCap(BigDecimal.valueOf(2_000_000_000_000L))
                .circulatingMarketCap(BigDecimal.valueOf(1_000_000_000_000L))
                .turnoverRate(new BigDecimal("0.0034"))
                .asOfDate(LocalDate.of(2026, 8, 1)).fetchedAt(LocalDateTime.now()).build();
        universeMapper.upsertBatch(List.of(row));
        row.setName("贵州茅台A");
        universeMapper.upsertBatch(List.of(row));
        assertEquals(1, universeMapper.countByAsOf(LocalDate.of(2026, 8, 1)));
        assertEquals(List.of("SH.600519"), universeMapper.selectSymbolsByAsOf(LocalDate.of(2026, 8, 1)));
        assertEquals(LocalDate.of(2026, 8, 1), universeMapper.selectLatestAsOfDate());
    }

    @Test
    void industryMembershipPitOverlapAndInvalidPeriodQueries() {
        MdfIndustryTaxonomyDO taxonomy = MdfIndustryTaxonomyDO.builder()
                .taxonomyCode("SINA_INDUSTRY").taxonomyName("新浪行业").providerCode("IMPORT_CSV_TAXONOMY")
                .isMutuallyExclusive(1).build();
        taxonomyMapper.upsert(taxonomy);
        MdfIndustryTaxonomyDO updated = MdfIndustryTaxonomyDO.builder()
                .taxonomyCode("SINA_INDUSTRY").taxonomyName("新浪行业V2").providerCode("IMPORT_CSV_TAXONOMY")
                .isMutuallyExclusive(1).build();
        taxonomyMapper.upsert(updated);
        assertEquals("新浪行业V2", taxonomyMapper.selectByCode("SINA_INDUSTRY").getTaxonomyName());

        LocalDateTime fetchedAt = LocalDateTime.now();
        membershipMapper.upsertBatch(List.of(
                membership("SINA_INDUSTRY", "new_blhy", "玻璃行业", "SH.600519",
                        LocalDate.of(2021, 1, 1), LocalDate.of(2023, 6, 30), fetchedAt),
                // 半开区间相邻（to=前一 from）不重叠
                membership("SINA_INDUSTRY", "new_blhy", "玻璃行业", "SH.600519",
                        LocalDate.of(2023, 7, 1), null, fetchedAt)));
        assertEquals(0, membershipMapper.countOverlapPairs("SINA_INDUSTRY"));
        assertEquals(0, membershipMapper.countInvalidPeriods("SINA_INDUSTRY"));
        assertEquals(2, membershipMapper.countByTaxonomy("SINA_INDUSTRY"));
        assertEquals(1, membershipMapper.countDistinctSymbols("SINA_INDUSTRY"));

        // 重叠区间 + 无效区间（to <= from）：[2022-01-01,∞) 同时穿越 [2021-01-01,2023-06-30) 与 [2023-07-01,∞) → 2 对
        membershipMapper.upsertBatch(List.of(
                membership("SINA_INDUSTRY", "new_jg", "机械行业", "SH.600519",
                        LocalDate.of(2022, 1, 1), null, fetchedAt),
                membership("SINA_INDUSTRY", "new_jg", "机械行业", "SZ.000001",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1), fetchedAt)));
        assertEquals(2, membershipMapper.countOverlapPairs("SINA_INDUSTRY"),
                "同 symbol 半开区间交叉必须被检出（新开区间与两条既有区间各成一对）");
        assertEquals(1, membershipMapper.countInvalidPeriods("SINA_INDUSTRY"));
    }

    @Test
    void coverageWatermarkUpsertIsIdempotent() {
        long versionId = newVersion();
        MdfCoverageWatermarkDO row = MdfCoverageWatermarkDO.builder()
                .datasetVersionId(versionId).canonicalSymbol("SH.600519")
                .firstDate(LocalDate.of(2026, 7, 1)).lastDate(LocalDate.of(2026, 7, 31))
                .rowCount(23L).expectedDays(23L).coveredDays(20L)
                .coverageRatio(new BigDecimal("0.86956522")).calculatedAt(LocalDateTime.now()).build();
        coverageMapper.upsertBatch(List.of(row));
        row.setCoveredDays(23L);
        row.setCoverageRatio(BigDecimal.ONE);
        coverageMapper.upsertBatch(List.of(row));
        List<MdfCoverageWatermarkDO> rows = coverageMapper.selectByVersion(versionId);
        assertEquals(1, rows.size());
        assertEquals(23L, rows.get(0).getCoveredDays());
    }

    @Test
    void backfillTaskClaimAndChunkLifecycle() {
        MdfBackfillTaskDO task = MdfBackfillTaskDO.builder()
                .datasetCode("TEST_DS").marketCode("CN").providerCode("TENCENT_PUBLIC").frequency("1D")
                .adjustType("NONE").startDate(LocalDate.of(2026, 7, 1)).endDate(LocalDate.of(2026, 7, 31))
                .symbolsJson("[\"SH.600519\"]").symbolsHash("hash-1").chunkSize(50).status("PENDING")
                .plannedCount(1).successCount(0).failCount(0).skipCount(0)
                .insertedCount(0L).updatedCount(0L).build();
        taskMapper.insert(task);

        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, taskMapper.tryClaim(task.getId(), "token-a", now, now.minusHours(1)));
        // 他人有效 claim 不可抢（RUNNING 状态也不允许再认领）
        assertEquals(0, taskMapper.tryClaim(task.getId(), "token-b", now, now.minusHours(1)));
        assertEquals(1, taskMapper.countActiveByScope("TEST_DS", "TENCENT_PUBLIC", "NONE",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "hash-1"));
        assertEquals(1, taskMapper.releaseClaim(task.getId(), "token-a"));
        assertEquals(0, taskMapper.releaseClaim(task.getId(), "token-a"));

        // RUNNING 可被暂停（PAUSED 释放 claim）
        taskMapper.tryClaim(task.getId(), "token-c", now, now.minusHours(1));
        assertEquals(1, taskMapper.pauseIfRunning(task.getId(), now));
        assertNull(taskMapper.selectById(task.getId()).getClaimToken());

        MdfBackfillChunkDO chunk = MdfBackfillChunkDO.builder()
                .taskId(task.getId()).chunkIndex(0).symbolsJson("[\"SH.600519\"]")
                .startDate(LocalDate.of(2026, 7, 1)).endDate(LocalDate.of(2026, 7, 31))
                .status("FAILED").attempts(1).insertedCount(0L).updatedCount(0L)
                .skippedCount(0L).failedCount(1L)
                .lastErrorCode("MARKET_DATA_PROVIDER_TIMEOUT").lastErrorMessage("timeout").build();
        chunkMapper.insertBatch(List.of(chunk));
        assertEquals(1, chunkMapper.resetFailedToPending(task.getId(), LocalDateTime.now()));
        MdfBackfillChunkDO reset = chunkMapper.selectByTaskId(task.getId()).get(0);
        assertEquals("PENDING", reset.getStatus());
        assertEquals(1, reset.getAttempts(), "重试不清空尝试次数");
        assertNull(reset.getLastErrorCode());
        assertEquals(1, chunkMapper.countByTaskAndStatus(task.getId(), "PENDING"));
    }

    @Test
    void importBatchUniqueAndQualityResultRewrite() {
        MdfImportBatchDO batch = MdfImportBatchDO.builder()
                .importKind("DAILY_BAR").providerCode("IMPORT_CSV_DAILY")
                .fileName("bars.csv").fileHash("hash-x")
                .insertedCount(3).updatedCount(0).skippedCount(0).rejectedCount(0)
                .status("COMPLETED").build();
        importBatchMapper.insert(batch);
        assertEquals(batch.getId(), importBatchMapper.selectByKindAndHash("DAILY_BAR", "hash-x").getId());
        assertTrue(importBatchMapper.selectList(null, 0, 10).size() >= 1);

        long versionId = newVersion();
        qualityResultMapper.insertBatch(List.of(result(versionId, "EMPTY_DATASET", "FAIL", 1L)));
        qualityResultMapper.insertBatch(List.of(result(versionId, "OHLC_VALIDITY", "FAIL", 2L)));
        qualityResultMapper.deleteByVersion(versionId);
        qualityResultMapper.insertBatch(List.of(
                result(versionId, "EMPTY_DATASET", "OK", 0L), result(versionId, "OHLC_VALIDITY", "OK", 0L)));
        assertEquals(2, qualityResultMapper.selectByVersion(versionId).size());
        assertEquals(0, qualityResultMapper.countFailByVersion(versionId));
        qualityResultMapper.deleteByVersion(versionId);
        qualityResultMapper.insertBatch(List.of(result(versionId, "OHLC_VALIDITY", "FAIL", 5L)));
        assertEquals(1, qualityResultMapper.countFailByVersion(versionId));
    }

    /** 建一个真实 dataset+version（coverage/quality 外键需要）。 */
    private long newVersion() {
        MdfDatasetDO dataset = MdfDatasetDO.builder()
                .datasetCode("TEST_DS_" + System.nanoTime()).datasetName("测试数据集").marketCode("CN")
                .barType("DAILY").frequency("1D").providerCode("TENCENT_PUBLIC").adjustType("NONE")
                .unitCaliber("元/股/元/小数").description("测试").build();
        datasetMapper.insert(dataset);
        MdfDatasetVersionDO version = MdfDatasetVersionDO.builder()
                .datasetId(dataset.getId()).versionCode("v1").status("DRAFT")
                .startDate(LocalDate.of(2026, 7, 1)).endDate(LocalDate.of(2026, 7, 31))
                .sourceProvider("TENCENT_PUBLIC").rowCount(0L).build();
        versionMapper.insert(version);
        return version.getId();
    }

    private MdfIndustryMembershipDO membership(String taxonomy, String industryCode, String industryName,
                                               String symbol, LocalDate from, LocalDate to, LocalDateTime fetchedAt) {
        return MdfIndustryMembershipDO.builder()
                .taxonomyCode(taxonomy).industryCode(industryCode).industryName(industryName)
                .canonicalSymbol(symbol).effectiveFrom(from).effectiveTo(to)
                .sourceProvider("IMPORT_CSV_PIT").fetchedAt(fetchedAt).build();
    }

    private MdfQualityResultDO result(long versionId, String code, String status, long affected) {
        return MdfQualityResultDO.builder().datasetVersionId(versionId).checkCode(code).status(status)
                .affectedCount(affected).detailJson("{}").checkedAt(LocalDateTime.now()).build();
    }
}
