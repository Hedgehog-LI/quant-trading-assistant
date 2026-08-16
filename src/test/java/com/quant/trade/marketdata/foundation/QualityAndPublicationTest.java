package com.quant.trade.marketdata.foundation;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.foundation.model.MdfCoverageWatermarkDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.model.MdfQualityResultDO;
import com.quant.trade.marketdata.foundation.service.DatasetPublicationService;
import com.quant.trade.marketdata.foundation.service.DataFoundationDatasetService;
import com.quant.trade.marketdata.foundation.service.DataQualityService;
import com.quant.trade.marketdata.foundation.service.SnapshotImportService;
import com.quant.trade.marketdata.foundation.service.VersionLineageService;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T11/T12 + R1 §四：质量门禁与发布（manifest 域 + 严格覆盖门槛 + 血缘冻结）。
 * 健康 → 16 检查族无 FAIL → QUALIFIED → 发布冻结血缘 → RELEASED（指针原子切换，旧版本 RETIRED）；
 * 空数据/OHLC 违法/口径混入 → REJECTED，发布被 DATA_FOUNDATION_QUALITY_GATE_FAILED 阻断。
 */
@SpringBootTest
@ActiveProfiles("test")
class QualityAndPublicationTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private static final LocalDate END = LocalDate.of(2026, 7, 3);

    @Autowired
    private DataFoundationDatasetService datasetService;
    @Autowired
    private DataQualityService qualityService;
    @Autowired
    private DatasetPublicationService publicationService;
    @Autowired
    private SnapshotImportService importService;
    @Autowired
    private VersionLineageService lineageService;
    @Autowired
    private StockDailyBarMapper stockDailyBarMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanFoundationTables() {
        FoundationTestTables.cleanAll(jdbcTemplate);
    }

    private String createImportDataset(String code) {
        datasetService.createDataset(code, code + " 名称", "CN", "DAILY", "1D", "IMPORT_CSV_DAILY", "NONE", "测试");
        return code;
    }

    private void importCalendarAndUniverse() {
        importService.importSnapshot("TRADING_CALENDAR", null, "cal.csv", ("""
                market_code,trade_date,is_trading_day
                CN,2026-07-01,true
                CN,2026-07-02,true
                CN,2026-07-03,true
                CN,2026-07-04,false
                """).getBytes(StandardCharsets.UTF_8));
        importService.importSnapshot("UNIVERSE_SNAPSHOT", null, "universe.csv", ("""
                symbol,name,market,total_market_cap,circulating_market_cap,turnover_rate,as_of_date
                SH.600519,贵州茅台,SH,2000000000000,1000000000000,0.0034,2026-07-01
                SZ.000001,平安银行,SZ,300000000000,250000000000,0.0052,2026-07-01
                """).getBytes(StandardCharsets.UTF_8));
    }

    /** R1 §七：DAILY_BAR 必须绑定 datasetVersionId；salt 为纯数字，变更末行金额使批次哈希不同。 */
    private void importDailyBars(long versionId, String salt) {
        importService.importSnapshot("DAILY_BAR", versionId, "bars-" + salt + ".csv", ("""
                symbol,trade_date,open,high,low,close,volume,amount
                SH.600519,2026-07-01,1700.00,1750.00,1690.00,1720.50,2500000,4301250000.00
                SH.600519,2026-07-02,1720.50,1730.00,1701.00,1715.25,1800000,3087450000.00
                SH.600519,2026-07-03,1715.25,1722.00,1700.00,1710.00,1600000,2736000000.00
                SZ.000001,2026-07-01,10.50,10.80,10.40,10.60,80000000,848000000.00
                SZ.000001,2026-07-02,10.60,10.75,10.50,10.55,70000000,738500000.00
                SZ.000001,2026-07-03,10.55,10.70,10.45,10.65,75000000,79875%s000.00
                """.formatted(salt)).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> statusByCode(List<MdfQualityResultDO> results) {
        return results.stream().collect(Collectors.toMap(
                MdfQualityResultDO::getCheckCode, MdfQualityResultDO::getStatus));
    }

    private void assertAllSixteenChecksPresent(Map<String, String> statusByCode) {
        Set<String> expected = Set.of("DATE_RANGE_COVERAGE", "UNIVERSE_COVERAGE", "DAILY_BAR_GAP",
                "DUPLICATE_ROWS", "OHLC_VALIDITY", "UNIT_ANOMALY", "NON_TRADING_DAY_ANOMALY",
                "INDUSTRY_MEMBERSHIP_OVERLAP", "INDUSTRY_MEMBERSHIP_INVALID_PERIOD",
                "UNMAPPED_INDUSTRY_SYMBOL", "PROVIDER_ADJUST_MIXING", "DATA_STALENESS", "EMPTY_DATASET",
                FoundationConstants.COVERAGE_GATE_CHECK, FoundationConstants.BOUNDARY_COVERAGE_CHECK,
                FoundationConstants.LINEAGE_DRIFT_CHECK);
        assertEquals(expected, statusByCode.keySet(), "16 检查族必须全部出现");
    }

    // ---------------------------------------------------------------- 场景 A：健康 → QUALIFIED → 发布冻结血缘 → RELEASED → 指针切换

    @Test
    void healthyDatasetQualifiesPublishesFreezesLineageAndRetiresOldRelease() {
        String code = createImportDataset("IMP_HEALTHY");
        importCalendarAndUniverse();
        MdfDatasetVersionDO v1 = datasetService.createVersion(code, START, END);
        importDailyBars(v1.getId(), "0");

        List<MdfQualityResultDO> results = qualityService.runChecks(v1.getId());
        Map<String, String> statusByCode = statusByCode(results);
        assertAllSixteenChecksPresent(statusByCode);
        assertTrue(statusByCode.values().stream().noneMatch("FAIL"::equals),
                "健康数据集不得出现 FAIL，实际=" + statusByCode + " 详情="
                        + results.stream().map(r -> r.getCheckCode() + ":" + r.getDetailJson())
                                .collect(Collectors.joining(" | ")));
        assertEquals("OK", statusByCode.get("EMPTY_DATASET"));
        assertEquals("OK", statusByCode.get(FoundationConstants.COVERAGE_GATE_CHECK), "总体覆盖 6/6 达阈值");
        assertEquals("OK", statusByCode.get(FoundationConstants.BOUNDARY_COVERAGE_CHECK), "首末边界全覆盖");

        v1 = datasetService.getVersion(v1.getId());
        assertEquals("QUALIFIED", v1.getStatus());
        assertNotNull(v1.getQualifiedAt());
        assertEquals(6L, v1.getRowCount(), "版本行数=manifest 行数（2 证券 × 3 日）");
        assertEquals(0, qualityService.countFail(v1.getId()));
        assertEquals(6L, lineageService.countManifest(v1.getId()), "导入行已入版本 manifest");

        List<MdfCoverageWatermarkDO> coverage = qualityService.listCoverage(v1.getId());
        assertEquals(2, coverage.size());
        coverage.forEach(row -> {
            assertEquals(3L, row.getRowCount());
            assertEquals(3L, row.getExpectedDays());
            assertEquals(0, row.getCoverageRatio().compareTo(java.math.BigDecimal.ONE));
        });

        MdfDatasetVersionDO released = publicationService.publish(v1.getId());
        assertEquals("RELEASED", released.getStatus());
        assertNotNull(released.getReleasedAt());
        assertNotNull(released.getContentHash(), "发布冻结内容哈希（R1 §六）");
        assertEquals(6L, released.getManifestRowCount());
        assertEquals(FoundationConstants.LINEAGE_FROZEN, released.getLineageStatus());
        assertEquals(v1.getId(), datasetService.getDataset(code).getCurrentVersionId());

        // released 只读端点返回血缘字段
        DatasetPublicationService.MdfDatasetVersionDTO releasedDto = publicationService.currentReleased(code);
        assertEquals(released.getContentHash(), releasedDto.contentHash());
        assertEquals(FoundationConstants.LINEAGE_FROZEN, releasedDto.lineageStatus());

        // v2：不同内容（不同批次哈希）→ 质量 → 发布：v1 RETIRED、指针切换
        MdfDatasetVersionDO v2 = datasetService.createVersion(code, START, END);
        importDailyBars(v2.getId(), "1");
        qualityService.runChecks(v2.getId());
        assertEquals("QUALIFIED", datasetService.getVersion(v2.getId()).getStatus());
        publicationService.publish(v2.getId());

        assertEquals("RETIRED", datasetService.getVersion(v1.getId()).getStatus(), "旧 RELEASED 版本退休（保留可查）");
        assertEquals("RELEASED", datasetService.getVersion(v2.getId()).getStatus());
        assertEquals(v2.getId(), datasetService.getDataset(code).getCurrentVersionId(), "指针切换到 v2");
        assertNotEquals(datasetService.getVersion(v1.getId()).getContentHash(),
                datasetService.getVersion(v2.getId()).getContentHash(), "两版本内容哈希不同（内容身份可区分）");
    }

    // ---------------------------------------------------------------- 场景 B：空数据 → REJECTED → 禁止发布

    @Test
    void emptyVersionIsRejectedAndBlockedFromPublish() {
        String code = createImportDataset("IMP_EMPTY");
        importCalendarAndUniverse();
        MdfDatasetVersionDO version = datasetService.createVersion(code, START, END);

        Map<String, String> statusByCode = statusByCode(qualityService.runChecks(version.getId()));
        assertAllSixteenChecksPresent(statusByCode);
        assertEquals("FAIL", statusByCode.get("EMPTY_DATASET"));
        assertEquals("FAIL", statusByCode.get(FoundationConstants.COVERAGE_GATE_CHECK), "无期望基准不得放行");
        assertEquals("REJECTED", datasetService.getVersion(version.getId()).getStatus());
        assertTrue(qualityService.countFail(version.getId()) >= 1);

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> publicationService.publish(version.getId()));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED.getCode(), blocked.getErrorCode().getCode());
        assertNull(datasetService.getDataset(code).getCurrentVersionId(), "失败版本不成为研究默认版本");
    }

    // ---------------------------------------------------------------- 场景 C：坏 OHLC（经 manifest 检出）→ REJECTED

    @Test
    void ohlcViolationFailsQualityGate() {
        String code = createImportDataset("IMP_BAD_OHLC");
        importCalendarAndUniverse();
        MdfDatasetVersionDO version = datasetService.createVersion(code, START, END);
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-01', 'NONE', 'IMPORT_CSV_DAILY',
                    11.50, 11.00, 11.30, 11.50, 100, 1150.00, ?)
                """, LocalDateTime.now());
        recordInsertedBarsToManifest(version.getId(), "SH.600519", "IMPORT_CSV_DAILY");

        Map<String, String> statusByCode = statusByCode(qualityService.runChecks(version.getId()));
        assertEquals("FAIL", statusByCode.get("OHLC_VALIDITY"), "high<low 必须被 manifest 检出");
        assertEquals("REJECTED", datasetService.getVersion(version.getId()).getStatus());

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> publicationService.publish(version.getId()));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED.getCode(), blocked.getErrorCode().getCode());
    }

    // ---------------------------------------------------------------- 场景 D：版本内混入其他 Provider（经 manifest）→ REJECTED

    @Test
    void providerMixingInsideVersionFailsQualityGate() {
        String code = createImportDataset("IMP_MIX");
        importCalendarAndUniverse();
        MdfDatasetVersionDO version = datasetService.createVersion(code, START, END);
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-01', 'NONE', 'IMPORT_CSV_DAILY',
                    10.00, 11.00, 9.50, 10.50, 1000, 10500.00, ?)
                """, LocalDateTime.now());
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-02', 'NONE', 'TENCENT_PUBLIC',
                    10.00, 11.00, 9.50, 10.50, 1000, 10500.00, ?)
                """, LocalDateTime.now());
        // 版本 manifest 纳入两行（含 TENCENT 行）→ 版本内口径混入被检出
        recordInsertedBarsToManifest(version.getId(), "SH.600519", null);

        Map<String, String> statusByCode = statusByCode(qualityService.runChecks(version.getId()));
        assertEquals("FAIL", statusByCode.get("PROVIDER_ADJUST_MIXING"), "版本 manifest 内声明口径之外来源必须 FAIL");
        assertEquals("REJECTED", datasetService.getVersion(version.getId()).getStatus());

        assertThrows(BusinessException.class, () -> publicationService.publish(version.getId()));
        assertNull(datasetService.getDataset(code).getCurrentVersionId());
    }

    // ---------------------------------------------------------------- R1：DAILY_BAR 未绑版本被拒（§七）

    @Test
    void dailyBarImportWithoutVersionIsRejected() {
        createImportDataset("IMP_NOVER");
        importService.importSnapshot("TRADING_CALENDAR", null, "cal.csv", ("""
                market_code,trade_date,is_trading_day
                CN,2026-07-01,true
                """).getBytes(StandardCharsets.UTF_8));
        byte[] bars =("""
                symbol,trade_date,open,high,low,close,volume,amount
                SH.600519,2026-07-01,10.00,11.00,9.50,10.50,1000,10500.00
                """).getBytes(StandardCharsets.UTF_8);
        BusinessException rejected = assertThrows(BusinessException.class,
                () -> importService.importSnapshot("DAILY_BAR", null, "bars.csv", bars));
        assertEquals(ErrorCodeEnum.VALIDATION_ERROR.getCode(), rejected.getErrorCode().getCode());
    }

    /** 直接 JDBC 插入的 bar 纳入 manifest（绕过导入通道，用于构造坏事实场景）。 */
    private void recordInsertedBarsToManifest(long versionId, String symbol, String dataSource) {
        List<StockDailyBarDO> bars = stockDailyBarMapper.selectByFilter(symbol, START, END, "NONE",
                dataSource, 100, 0);
        lineageService.recordBars(versionId, bars, FoundationConstants.LINEAGE_SOURCE_IMPORT, 0L);
    }
}
