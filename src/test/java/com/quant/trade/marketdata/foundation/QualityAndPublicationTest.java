package com.quant.trade.marketdata.foundation;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.foundation.model.MdfCoverageWatermarkDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.model.MdfQualityResultDO;
import com.quant.trade.marketdata.foundation.service.SnapshotImportService;
import com.quant.trade.marketdata.foundation.service.DataFoundationDatasetService;
import com.quant.trade.marketdata.foundation.service.DataQualityService;
import com.quant.trade.marketdata.foundation.service.DatasetPublicationService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T11/T12：质量门禁与发布（契约 AC-05）。
 * 健康数据集 13 检查族全出现且无 FAIL → QUALIFIED → RELEASED（指针原子切换，旧版本 RETIRED）；
 * 空数据/OHLC 违法/口径混用 → REJECTED，发布被 DATA_FOUNDATION_QUALITY_GATE_FAILED 阻断。
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

    private void importHealthyFixture() {
        importService.importSnapshot("TRADING_CALENDAR", "cal.csv", ("""
                market_code,trade_date,is_trading_day
                CN,2026-07-01,true
                CN,2026-07-02,true
                CN,2026-07-03,true
                CN,2026-07-04,false
                """).getBytes(StandardCharsets.UTF_8));
        importService.importSnapshot("UNIVERSE_SNAPSHOT", "universe.csv", ("""
                symbol,name,market,total_market_cap,circulating_market_cap,turnover_rate,as_of_date
                SH.600519,贵州茅台,SH,2000000000000,1000000000000,0.0034,2026-07-01
                SZ.000001,平安银行,SZ,300000000000,250000000000,0.0052,2026-07-01
                """).getBytes(StandardCharsets.UTF_8));
        importService.importSnapshot("DAILY_BAR", "bars.csv", ("""
                symbol,trade_date,open,high,low,close,volume,amount
                SH.600519,2026-07-01,1700.00,1750.00,1690.00,1720.50,2500000,4301250000.00
                SH.600519,2026-07-02,1720.50,1730.00,1701.00,1715.25,1800000,3087450000.00
                SH.600519,2026-07-03,1715.25,1722.00,1700.00,1710.00,1600000,2736000000.00
                SZ.000001,2026-07-01,10.50,10.80,10.40,10.60,80000000,848000000.00
                SZ.000001,2026-07-02,10.60,10.75,10.50,10.55,70000000,738500000.00
                SZ.000001,2026-07-03,10.55,10.70,10.45,10.65,75000000,798750000.00
                """).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> statusByCode(List<MdfQualityResultDO> results) {
        return results.stream().collect(Collectors.toMap(
                MdfQualityResultDO::getCheckCode, MdfQualityResultDO::getStatus));
    }

    private void assertAllThirteenChecksPresent(Map<String, String> statusByCode) {
        Set<String> expected = Set.of("DATE_RANGE_COVERAGE", "UNIVERSE_COVERAGE", "DAILY_BAR_GAP",
                "DUPLICATE_ROWS", "OHLC_VALIDITY", "UNIT_ANOMALY", "NON_TRADING_DAY_ANOMALY",
                "INDUSTRY_MEMBERSHIP_OVERLAP", "INDUSTRY_MEMBERSHIP_INVALID_PERIOD",
                "UNMAPPED_INDUSTRY_SYMBOL", "PROVIDER_ADJUST_MIXING", "DATA_STALENESS", "EMPTY_DATASET");
        assertEquals(expected, statusByCode.keySet(), "13 检查族必须全部出现");
    }

    // ---------------------------------------------------------------- 场景 A：健康 → QUALIFIED → RELEASED → 指针切换

    @Test
    void healthyDatasetQualifiesPublishesAndRetiresOldRelease() {
        String code = createImportDataset("IMP_HEALTHY");
        importHealthyFixture();
        MdfDatasetVersionDO v1 = datasetService.createVersion(code, START, END);

        List<MdfQualityResultDO> results = qualityService.runChecks(v1.getId());
        Map<String, String> statusByCode = statusByCode(results);
        assertAllThirteenChecksPresent(statusByCode);
        assertTrue(statusByCode.values().stream().noneMatch("FAIL"::equals), "健康数据集不得出现 FAIL");
        assertEquals("OK", statusByCode.get("EMPTY_DATASET"));
        assertEquals("OK", statusByCode.get("OHLC_VALIDITY"));
        assertEquals("OK", statusByCode.get("DUPLICATE_ROWS"));
        assertEquals("OK", statusByCode.get("NON_TRADING_DAY_ANOMALY"));
        assertEquals("OK", statusByCode.get("PROVIDER_ADJUST_MIXING"));

        v1 = datasetService.getVersion(v1.getId());
        assertEquals("QUALIFIED", v1.getStatus());
        assertNotNull(v1.getQualifiedAt());
        assertEquals(6L, v1.getRowCount(), "版本行数=事实行数（2 证券 × 3 日）");
        assertEquals(0, qualityService.countFail(v1.getId()));

        List<MdfCoverageWatermarkDO> coverage = qualityService.listCoverage(v1.getId());
        assertEquals(2, coverage.size());
        coverage.forEach(row -> {
            assertEquals(3L, row.getRowCount());
            assertEquals(3L, row.getExpectedDays());
            assertEquals(3L, row.getCoveredDays());
            assertEquals(0, row.getCoverageRatio().compareTo(java.math.BigDecimal.ONE));
        });

        MdfDatasetVersionDO released = publicationService.publish(v1.getId());
        assertEquals("RELEASED", released.getStatus());
        assertNotNull(released.getReleasedAt());
        assertEquals(v1.getId(), datasetService.getDataset(code).getCurrentVersionId(),
                "发布后 dataset.current_version_id 指向该版本");

        // v2 重复导同数据 → 再质量 → 再发布：v1 RETIRED、指针切换（原子语义）
        MdfDatasetVersionDO v2 = datasetService.createVersion(code, START, END);
        qualityService.runChecks(v2.getId());
        assertEquals("QUALIFIED", datasetService.getVersion(v2.getId()).getStatus());
        publicationService.publish(v2.getId());

        assertEquals("RETIRED", datasetService.getVersion(v1.getId()).getStatus(), "旧 RELEASED 版本退休（保留可查）");
        assertNotNull(datasetService.getVersion(v1.getId()).getReleasedAt());
        assertEquals("RELEASED", datasetService.getVersion(v2.getId()).getStatus());
        assertEquals(v2.getId(), datasetService.getDataset(code).getCurrentVersionId(), "指针切换到 v2");
    }

    // ---------------------------------------------------------------- 场景 B：空数据 → REJECTED → 禁止发布

    @Test
    void emptyVersionIsRejectedAndBlockedFromPublish() {
        String code = createImportDataset("IMP_EMPTY");
        MdfDatasetVersionDO version = datasetService.createVersion(code, START, END);

        Map<String, String> statusByCode = statusByCode(qualityService.runChecks(version.getId()));
        assertAllThirteenChecksPresent(statusByCode);
        assertEquals("FAIL", statusByCode.get("EMPTY_DATASET"));
        assertEquals("REJECTED", datasetService.getVersion(version.getId()).getStatus());
        assertEquals(1, qualityService.countFail(version.getId()));

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> publicationService.publish(version.getId()));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED.getCode(), blocked.getErrorCode().getCode());
        assertNull(datasetService.getDataset(code).getCurrentVersionId(), "失败版本不成为研究默认版本");
    }

    // ---------------------------------------------------------------- 场景 C：坏 OHLC → REJECTED

    @Test
    void ohlcViolationFailsQualityGate() {
        String code = createImportDataset("IMP_BAD_OHLC");
        MdfDatasetVersionDO version = datasetService.createVersion(code, START, END);
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-01', 'NONE', 'IMPORT_CSV_DAILY',
                    11.50, 11.00, 11.30, 11.50, 100, 1150.00, ?)
                """, LocalDateTime.now());

        Map<String, String> statusByCode = statusByCode(qualityService.runChecks(version.getId()));
        assertEquals("FAIL", statusByCode.get("OHLC_VALIDITY"), "high<low 必须被检出");
        assertEquals("REJECTED", datasetService.getVersion(version.getId()).getStatus());

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> publicationService.publish(version.getId()));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED.getCode(), blocked.getErrorCode().getCode());
    }

    // ---------------------------------------------------------------- 场景 D：口径混用 → REJECTED

    @Test
    void providerMixingFailsQualityGate() {
        String code = createImportDataset("IMP_MIX");
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

        Map<String, String> statusByCode = statusByCode(qualityService.runChecks(version.getId()));
        assertEquals("FAIL", statusByCode.get("PROVIDER_ADJUST_MIXING"), "声明口径之外的来源必须被检出");
        assertEquals("REJECTED", datasetService.getVersion(version.getId()).getStatus());

        assertThrows(BusinessException.class, () -> publicationService.publish(version.getId()));
        assertNull(datasetService.getDataset(code).getCurrentVersionId());
    }
}
