package com.quant.trade.marketdata.foundation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.foundation.dao.MdfIndustryMembershipMapper;
import com.quant.trade.marketdata.foundation.model.MdfImportBatchDO;
import com.quant.trade.marketdata.foundation.service.DataFoundationDatasetService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T08/T09/T10：无凭据 CSV 导入通道（契约 AC-04）。
 * 五类 schema 合法样例、行级错误报告、文件内重复键 skipped、表头错误整批拒绝、
 * 同内容重复导入幂等（file_hash）、PIT 成分重叠防线、日历幂等。
 */
@SpringBootTest
@ActiveProfiles("test")
class CsvSnapshotImportTest {

    @Autowired
    private SnapshotImportService importService;
    @Autowired
    private DataFoundationDatasetService datasetService;
    @Autowired
    private MdfIndustryMembershipMapper membershipMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void cleanFoundationTables() {
        FoundationTestTables.cleanAll(jdbcTemplate);
    }

    /** R1 §七：DAILY_BAR 导入必绑导入类版本。 */
    private Long newDailyVersion(String code) {
        datasetService.createDataset(code, code + " 名称", "CN", "DAILY", "1D", "IMPORT_CSV_DAILY", "NONE", "测试");
        return datasetService.createVersion(code, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)).getId();
    }

    private byte[] bytes(String csv) {
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private long count(String table) {
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + table, Long.class);
        return rows == null ? 0 : rows;
    }

    private String errorCode(String kind, Long versionId, String fileName, String csv) {
        return assertThrows(BusinessException.class,
                () -> importService.importSnapshot(kind, versionId, fileName, bytes(csv))).getErrorCode().getCode();
    }

    // ---------------------------------------------------------------- T08 五类合法样例

    @Test
    void fiveKindValidSamplesImportCleanly() {
        MdfImportBatchDO universe = importService.importSnapshot("UNIVERSE_SNAPSHOT", null, "universe.csv", bytes("""
                symbol,name,market,total_market_cap,circulating_market_cap,turnover_rate,as_of_date
                SH.600519,贵州茅台,SH,2000000000000,1000000000000,0.0034,2026-07-01
                SZ.000001,平安银行,SZ,300000000000,250000000000,0.0052,2026-07-01
                """));
        assertEquals(2, universe.getInsertedCount());
        assertNull(universe.getErrorReportJson());
        assertEquals(2, count("mdf_universe_snapshot"));
        assertEquals("IMPORT_CSV_UNIVERSE", universe.getProviderCode());
        assertEquals(2, count("stock_basic"), "证券身份登记复用 stock_basic");

        MdfImportBatchDO calendar = importService.importSnapshot("TRADING_CALENDAR", null, "calendar.csv", bytes("""
                market_code,trade_date,is_trading_day
                CN,2026-07-01,true
                CN,2026-07-04,false
                """));
        assertEquals(2, calendar.getInsertedCount());
        assertNull(calendar.getErrorReportJson());
        assertEquals(2, count("market_calendar"), "交易日历复用 market_calendar（不建新表）");

        Long barsVersion = newDailyVersion("IMP_CSV_X1");
        MdfImportBatchDO bars = importService.importSnapshot("DAILY_BAR", barsVersion, "bars.csv", bytes("""
                symbol,trade_date,open,high,low,close,volume,amount
                SH.600519,2026-07-01,1700.00,1750.00,1690.00,1720.50,2500000,4290125000.00
                SH.600519,2026-07-02,1720.50,1730.00,1701.00,1715.25,1800000,3087450000.00
                """));
        assertEquals(2, bars.getInsertedCount());
        assertNull(bars.getErrorReportJson());
        assertEquals(2, count("stock_daily_bar"), "日 K 事实写既有 stock_daily_bar");
        BigDecimal amount = jdbcTemplate.queryForObject(
                "SELECT amount FROM stock_daily_bar WHERE trade_date = '2026-07-01'", BigDecimal.class);
        assertEquals(0, amount.compareTo(new BigDecimal("4290125000.00")), "单位冻结：amount=元，不做万元换算");

        MdfImportBatchDO taxonomy = importService.importSnapshot("INDUSTRY_TAXONOMY", null, "taxonomy.csv", bytes("""
                taxonomy_code,taxonomy_name,provider_code,note
                SINA_INDUSTRY,新浪行业,IMPORT_CSV_TAXONOMY,非申万
                """));
        assertEquals(1, taxonomy.getInsertedCount());
        assertNull(taxonomy.getErrorReportJson());
        assertEquals(1, count("mdf_industry_taxonomy"));

        MdfImportBatchDO membership = importService.importSnapshot("INDUSTRY_MEMBERSHIP_PIT", null, "membership.csv", bytes("""
                taxonomy_code,industry_code,industry_name,symbol,effective_from,effective_to
                SINA_INDUSTRY,new_blhy,玻璃行业,SH.600519,2021-01-01,2023-06-30
                SINA_INDUSTRY,new_blhy,玻璃行业,SH.600519,2023-07-01,
                """));
        assertEquals(2, membership.getInsertedCount());
        assertNull(membership.getErrorReportJson());
        assertEquals(2, count("mdf_industry_membership"));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT effective_to FROM mdf_industry_membership WHERE effective_from = '2023-07-01'", java.time.LocalDate.class),
                "空 effective_to 落库为 NULL（至今）");
    }

    // ---------------------------------------------------------------- T08 行级错误

    @Test
    void rowLevelErrorsAreRejectedWithParseableReport() throws Exception {
        MdfImportBatchDO batch = importService.importSnapshot("DAILY_BAR", newDailyVersion("IMP_CSV_X2"), "bad-rows.csv", bytes("""
                symbol,trade_date,open,high,low,close,volume,amount
                ,2026-07-01,10,11,9,10,100,1000
                SH.600519,2026/07/01,10,11,9,10,100,1000
                SH.600519,2026-07-02,10,9,11,10,100,1000
                SH.600519,2026-07-03,10,11,9,10,-5,1000
                SH.600519,2026-07-06,10,11,9,10.5,100,1050
                """));
        assertEquals(1, batch.getInsertedCount());
        assertEquals(4, batch.getRejectedCount());
        assertEquals(0, batch.getSkippedCount());
        assertEquals("COMPLETED", batch.getStatus());

        JsonNode report = objectMapper.readTree(batch.getErrorReportJson());
        assertTrue(report.isArray());
        assertEquals(4, report.size());
        for (JsonNode error : report) {
            assertTrue(error.path("recordNumber").asInt() > 0);
            assertTrue(error.path("reason").asText().length() > 0);
        }
        assertEquals(1, count("stock_daily_bar"), "只有合法行落库，坏行不阻塞合法行");
    }

    @Test
    void membershipInvalidPeriodRowsAreRejected() throws Exception {
        MdfImportBatchDO batch = importService.importSnapshot("INDUSTRY_MEMBERSHIP_PIT", null, "bad-membership.csv", bytes("""
                taxonomy_code,industry_code,industry_name,symbol,effective_from,effective_to
                SINA_INDUSTRY,new_jg,机械行业,SH.600519,2021-01-01,2021-01-01
                SINA_INDUSTRY,new_jg,机械行业,SZ.000001,2021-01-01,2020-12-31
                SINA_INDUSTRY,new_jg,机械行业,SZ.000002,2021-01-01,
                """));
        assertEquals(1, batch.getInsertedCount());
        assertEquals(2, batch.getRejectedCount());
        JsonNode report = objectMapper.readTree(batch.getErrorReportJson());
        assertEquals(2, report.size());
        assertTrue(report.get(0).path("reason").asText().contains("effective_to"));
    }

    // ---------------------------------------------------------------- T08 文件内重复键

    @Test
    void inFileDuplicateUniqueKeysCountAsSkipped() {
        MdfImportBatchDO batch = importService.importSnapshot("DAILY_BAR", newDailyVersion("IMP_CSV_X3"), "dup.csv", bytes("""
                symbol,trade_date,open,high,low,close,volume,amount
                SH.600519,2026-07-01,10,11,9,10,100,1000
                SH.600519,2026-07-01,10,11,9,10,100,1000
                SH.600519,2026-07-02,10,11,9,10,100,1000
                """));
        assertEquals(2, batch.getInsertedCount());
        assertEquals(1, batch.getSkippedCount());
        assertEquals(2, count("stock_daily_bar"));

        MdfImportBatchDO membership = importService.importSnapshot("INDUSTRY_MEMBERSHIP_PIT", null, "dup-m.csv", bytes("""
                taxonomy_code,industry_code,industry_name,symbol,effective_from,effective_to
                SINA_INDUSTRY,new_blhy,玻璃行业,SH.600519,2021-01-01,2022-01-01
                SINA_INDUSTRY,new_blhy,玻璃行业,SH.600519,2021-01-01,2023-01-01
                """));
        assertEquals(1, membership.getInsertedCount());
        assertEquals(1, membership.getSkippedCount());
    }

    // ---------------------------------------------------------------- T08 表头错误整批拒绝

    @Test
    void wrongOrUnparseableHeaderRejectsWholeFile() {
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_IMPORT_FILE_INVALID.getCode(),
                errorCode("DAILY_BAR", newDailyVersion("IMP_CSV_X4"), "wrong-header.csv", """
                        ticker,date,open,high,low,close,volume,amount
                        SH.600519,2026-07-01,10,11,9,10,100,1000
                        """));
        assertEquals(0, count("stock_daily_bar"));

        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_IMPORT_FILE_INVALID.getCode(),
                errorCode("DAILY_BAR", newDailyVersion("IMP_CSV_X5"), "unterminated-quote.csv", """
                        symbol,trade_date,open,high,low,close,volume,amount
                        "SH.600519,2026-07-01,10,11,9,10,100,1000
                        """));

        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_IMPORT_KIND_INVALID.getCode(),
                errorCode("NOT_A_KIND", null, "x.csv", """
                        symbol,trade_date,open,high,low,close,volume,amount
                        SH.600519,2026-07-01,10,11,9,10,100,1000
                        """));
    }

    // ---------------------------------------------------------------- T09 幂等

    @Test
    void sameBytesReimportReturnsSameBatchAndChangesNothing() {
        String csv = """
                symbol,trade_date,open,high,low,close,volume,amount
                SH.600519,2026-07-01,10,11,9,10,100,1000
                SH.600519,2026-07-02,10,11,9,10,100,1000
                """;
        MdfImportBatchDO first = importService.importSnapshot("DAILY_BAR",
                newDailyVersion("IMP_CSV_X6"), "bars.csv", bytes(csv));
        MdfImportBatchDO second = importService.importSnapshot("DAILY_BAR",
                first.getDatasetVersionId(), "bars-again.csv", bytes(csv));

        assertEquals(first.getId(), second.getId(), "同 kind+file_hash 返回既有批次");
        assertEquals(first.getInsertedCount(), second.getInsertedCount());
        assertEquals(2, count("stock_daily_bar"));
        assertEquals(1, count("mdf_import_batch"));
    }

    @Test
    void calendarReimportWithDifferentBytesKeepsRowSetStable() {
        importService.importSnapshot("TRADING_CALENDAR", null, "cal-a.csv", bytes("""
                market_code,trade_date,is_trading_day
                CN,2026-07-01,true
                CN,2026-07-02,true
                """));
        // 不同字节（顺序不同 → hash 不同）：更新既有行，不产生重复
        MdfImportBatchDO second = importService.importSnapshot("TRADING_CALENDAR", null, "cal-b.csv", bytes("""
                market_code,trade_date,is_trading_day
                CN,2026-07-02,true
                CN,2026-07-01,true
                """));
        assertEquals(2, second.getInsertedCount());
        assertEquals(2, count("market_calendar"), "market_calendar 幂等：重复导入不翻倍");
    }

    // ---------------------------------------------------------------- T10 PIT 成分重叠

    @Test
    void membershipPitOverlapGuardedInsideFile() throws Exception {
        MdfImportBatchDO batch = importService.importSnapshot("INDUSTRY_MEMBERSHIP_PIT", null, "overlap.csv", bytes("""
                taxonomy_code,industry_code,industry_name,symbol,effective_from,effective_to
                SINA_INDUSTRY,new_blhy,玻璃行业,SH.600519,2021-01-01,2022-01-01
                SINA_INDUSTRY,new_blhy,玻璃行业,SH.600519,2022-01-01,
                SINA_INDUSTRY,new_jg,机械行业,SH.600519,2021-06-01,2021-09-01
                SINA_INDUSTRY,new_jg,机械行业,SZ.000001,2021-01-01,
                """));
        assertEquals(3, batch.getInsertedCount());
        assertEquals(1, batch.getRejectedCount());

        JsonNode report = objectMapper.readTree(batch.getErrorReportJson());
        assertTrue(report.get(0).path("reason").asText().contains("重叠"), "区间交叉行必须给出重叠原因");

        assertEquals(0, membershipMapper.countOverlapPairs("SINA_INDUSTRY"),
                "导入防线生效：落库后无重叠对（相邻半开区间不判重叠）");
        assertEquals(2, membershipMapper.countDistinctSymbols("SINA_INDUSTRY"));
        assertNotNull(membershipMapper.countByTaxonomy("SINA_INDUSTRY"));
    }
}
