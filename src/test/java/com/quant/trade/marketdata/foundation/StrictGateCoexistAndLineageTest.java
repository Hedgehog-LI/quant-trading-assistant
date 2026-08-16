package com.quant.trade.marketdata.foundation;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.model.MdfQualityResultDO;
import com.quant.trade.marketdata.foundation.service.DataFoundationDatasetService;
import com.quant.trade.marketdata.foundation.service.DataQualityService;
import com.quant.trade.marketdata.foundation.service.DatasetPublicationService;
import com.quant.trade.marketdata.foundation.service.SnapshotImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1 §四/§五/§六：严格质量门禁 + 多 Provider 合法共存 + 血缘漂移检测。
 * 覆盖率低于阈值/边界缺失/截断模拟 → FAIL 不可发布；同窗其他 Provider 行不致 FAIL；
 * 冻结后底层事实漂移 → LINEAGE_DRIFT FAIL + DRIFTED + 发布阻断。
 */
@SpringBootTest
@ActiveProfiles("test")
class StrictGateCoexistAndLineageTest {

    @Autowired
    private DataFoundationDatasetService datasetService;
    @Autowired
    private DataQualityService qualityService;
    @Autowired
    private DatasetPublicationService publicationService;
    @Autowired
    private SnapshotImportService importService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanFoundationTables() {
        FoundationTestTables.cleanAll(jdbcTemplate);
    }

    private Map<String, String> statuses(long versionId) {
        return qualityService.runChecks(versionId).stream().collect(Collectors.toMap(
                MdfQualityResultDO::getCheckCode, MdfQualityResultDO::getStatus));
    }

    private void importCalendar(String dates) {
        importService.importSnapshot("TRADING_CALENDAR", null, "cal.csv",
                ("market_code,trade_date,is_trading_day\n" + dates).getBytes(StandardCharsets.UTF_8));
    }

    private void importBars(long versionId, String rows) {
        importService.importSnapshot("DAILY_BAR", versionId, "bars.csv",
                ("symbol,trade_date,open,high,low,close,volume,amount\n" + rows).getBytes(StandardCharsets.UTF_8));
    }

    private String bar(String symbol, String date) {
        return "%s,%s,10.00,11.00,9.50,10.50,1000,10500.00\n".formatted(symbol, date);
    }

    // ---------------------------------------------------------------- §四 严格门禁

    @Test
    void incompleteSixYearWindowCannotQualifyOrPublish() {
        datasetService.createDataset("SIX_YEAR", "六年窗口", "CN", "DAILY", "1D", "IMPORT_CSV_DAILY", "NONE", "测试");
        importCalendar("CN,2021-01-04,true\nCN,2021-01-05,true\nCN,2026-07-01,true\nCN,2026-07-02,true\n");
        MdfDatasetVersionDO version = datasetService.createVersion("SIX_YEAR",
                LocalDate.of(2021, 1, 4), LocalDate.of(2026, 7, 2));
        // 只有 2021 两天数据（截断/严重不完整）：总体覆盖 2/4=0.5、末日边界 0
        importBars(version.getId(), bar("SH.600519", "2021-01-04") + bar("SH.600519", "2021-01-05"));

        Map<String, String> statusByCode = statuses(version.getId());
        assertEquals("FAIL", statusByCode.get(FoundationConstants.COVERAGE_GATE_CHECK), "总体覆盖低于 0.90 必须 FAIL");
        assertEquals("FAIL", statusByCode.get(FoundationConstants.BOUNDARY_COVERAGE_CHECK), "末日边界缺失必须 FAIL");
        assertEquals("REJECTED", datasetService.getVersion(version.getId()).getStatus());

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> publicationService.publish(version.getId()));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED.getCode(), blocked.getErrorCode().getCode());
    }

    @Test
    void truncatedTailSimulationRejectedByBoundaryGate() {
        datasetService.createDataset("TRUNC_DS", "截断模拟", "CN", "DAILY", "1D", "IMPORT_CSV_DAILY", "NONE", "测试");
        // 模拟 640 条截断：日历覆盖全窗，但事实只到倒数第二天
        importCalendar("CN,2026-07-01,true\nCN,2026-07-02,true\nCN,2026-07-03,true\n");
        MdfDatasetVersionDO version = datasetService.createVersion("TRUNC_DS",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
        importBars(version.getId(),
                bar("SH.600519", "2026-07-01") + bar("SH.600519", "2026-07-02"));

        Map<String, String> statusByCode = statuses(version.getId());
        assertEquals("FAIL", statusByCode.get(FoundationConstants.BOUNDARY_COVERAGE_CHECK),
                "尾部截断（末日无数据）必须被边界门禁拒绝");
        assertEquals("REJECTED", datasetService.getVersion(version.getId()).getStatus());
    }

    @Test
    void missingCalendarFailsCoverageGateInsteadOfSilentPass() {
        datasetService.createDataset("NO_CAL", "无日历", "CN", "DAILY", "1D", "IMPORT_CSV_DAILY", "NONE", "测试");
        MdfDatasetVersionDO version = datasetService.createVersion("NO_CAL",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
        importBars(version.getId(), bar("SH.600519", "2026-07-01"));
        Map<String, String> statusByCode = statuses(version.getId());
        assertEquals("FAIL", statusByCode.get(FoundationConstants.COVERAGE_GATE_CHECK), "无日历基准=无法证明覆盖→FAIL");
        assertEquals("FAIL", statusByCode.get(FoundationConstants.BOUNDARY_COVERAGE_CHECK));
    }

    // ---------------------------------------------------------------- §五 多 Provider 合法共存

    @Test
    void otherProviderFactsInSameWindowDoNotFailVersion() {
        datasetService.createDataset("COEXIST", "共存", "CN", "DAILY", "1D", "IMPORT_CSV_DAILY", "NONE", "测试");
        importCalendar("CN,2026-07-01,true\nCN,2026-07-02,true\nCN,2026-07-03,true\n");
        MdfDatasetVersionDO version = datasetService.createVersion("COEXIST",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
        importBars(version.getId(), bar("SH.600519", "2026-07-01") + bar("SH.600519", "2026-07-02")
                + bar("SH.600519", "2026-07-03"));

        // 同窗口同证券注入腾讯与 Longbridge 合法事实（不在版本 manifest 内）
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519','2026-07-02','NONE','TENCENT_PUBLIC',20,21,19,20.5,2000,41000.00,NOW()),
                       ('SH.600519','2026-07-02','NONE','LONGPORT',21,22,20,21.5,2100,44100.00,NOW()),
                       ('SZ.000001','2026-07-02','NONE','TENCENT_PUBLIC',5,6,4,5.5,500,2750.00,NOW())
                """);

        Map<String, String> statusByCode = statuses(version.getId());
        assertEquals("OK", statusByCode.get(FoundationConstants.CHECK_PROVIDER_ADJUST_MIXING),
                "版本外其他 Provider 合法事实不得判 FAIL（R1 §五）");
        assertTrue(statusByCode.values().stream().noneMatch("FAIL"::equals),
                "共存场景整体可 QUALIFIED，实际=" + statusByCode);
        assertEquals("QUALIFIED", datasetService.getVersion(version.getId()).getStatus());

        MdfDatasetVersionDO released = publicationService.publish(version.getId());
        assertEquals("RELEASED", released.getStatus());
        assertNotNull(released.getContentHash());
    }

    // ---------------------------------------------------------------- §六 血缘漂移检测

    @Test
    void frozenLineageDetectsUnderlyingDriftAndBlocksRepublish() {
        datasetService.createDataset("DRIFT_DS", "漂移", "CN", "DAILY", "1D", "IMPORT_CSV_DAILY", "NONE", "测试");
        importCalendar("CN,2026-07-01,true\nCN,2026-07-02,true\n");
        MdfDatasetVersionDO version = datasetService.createVersion("DRIFT_DS",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2));
        importBars(version.getId(), bar("SH.600519", "2026-07-01") + bar("SH.600519", "2026-07-02"));

        // 先跑检查使版本进入 QUALIFIED
        Map<String, String> preCheck = statuses(version.getId());
        assertTrue(preCheck.values().stream().noneMatch("FAIL"::equals), "冻结前健康=" + preCheck);
        assertEquals("QUALIFIED", datasetService.getVersion(version.getId()).getStatus());
        publicationService.publish(version.getId());
        MdfDatasetVersionDO frozen = datasetService.getVersion(version.getId());
        assertEquals(FoundationConstants.LINEAGE_FROZEN, frozen.getLineageStatus());
        assertEquals(2L, frozen.getManifestRowCount());
        String frozenHash = frozen.getContentHash();

        // 底层事实漂移：篡改未在 manifest 重算路径中的 bar 内容
        jdbcTemplate.update(
                "UPDATE stock_daily_bar SET close_price = 99.99 WHERE canonical_symbol = 'SH.600519' "
                        + "AND trade_date = '2026-07-01' AND data_source = 'IMPORT_CSV_DAILY'");

        Map<String, String> statusByCode = statuses(version.getId());
        assertEquals("FAIL", statusByCode.get(FoundationConstants.LINEAGE_DRIFT_CHECK),
                "冻结后底层事实漂移必须检出");
        assertEquals(FoundationConstants.LINEAGE_DRIFTED, datasetService.getVersion(version.getId()).getLineageStatus());

        // 漂移版本禁止再次发布（RETIRED 状态本身也拒绝；这里以状态或门禁双拒断言）
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> publicationService.publish(version.getId()));
        assertTrue(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED.getCode()
                        .equals(blocked.getErrorCode().getCode())
                        || ErrorCodeEnum.DATA_FOUNDATION_VERSION_NOT_FOUND.getCode()
                        .equals(blocked.getErrorCode().getCode()),
                "漂移/退休版本不得再次发布");

        // 冻结哈希未被静默刷新（阻断"静默可复现"）
        assertEquals(frozenHash, datasetService.getVersion(version.getId()).getContentHash());
    }

    // ---------------------------------------------------------------- §七 版本口径不匹配拒绝

    @Test
    void dailyBarImportRejectsProviderMismatchTargetVersion() {
        // TENCENT 回补数据集（非导入类）上的版本不得接收 CSV DAILY_BAR 导入
        datasetService.createDataset("TC_DS", "腾讯数据集", "CN", "DAILY", "1D", "TENCENT_PUBLIC", "NONE", "测试");
        jdbcTemplate.update("""
                INSERT INTO mdf_dataset_version (dataset_id, version_code, status, start_date, end_date,
                    source_provider, row_count)
                VALUES ((SELECT id FROM mdf_dataset WHERE dataset_code='TC_DS'), 'v9', 'DRAFT',
                    '2026-07-01', '2026-07-02', 'TENCENT_PUBLIC', 0)
                """);
        Long versionId = jdbcTemplate.queryForObject(
                "SELECT id FROM mdf_dataset_version WHERE version_code = 'v9'", Long.class);

        BusinessException rejected = assertThrows(BusinessException.class, () -> importService.importSnapshot(
                "DAILY_BAR", versionId, "bars.csv",
                ("symbol,trade_date,open,high,low,close,volume,amount\n" + bar("SH.600519", "2026-07-01"))
                        .getBytes(StandardCharsets.UTF_8)));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_DATASET_CONFLICT.getCode(), rejected.getErrorCode().getCode(),
                "目标版本 Provider 口径不符必须拒绝");
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_daily_bar WHERE data_source = 'IMPORT_CSV_DAILY'", Long.class));
    }
}
