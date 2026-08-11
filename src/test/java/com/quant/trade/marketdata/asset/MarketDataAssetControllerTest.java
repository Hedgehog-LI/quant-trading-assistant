package com.quant.trade.marketdata.asset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * P1.9-A availability 聚焦测试：controller → service → manager → mapper 全链路（H2）。
 * <p>
 * 只读断言，不写 provider、不触发采集；证券不存在 404，非法代码 400。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketDataAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM stock_alias");
        jdbcTemplate.update("DELETE FROM stock_daily_bar");
        jdbcTemplate.update("DELETE FROM stock_minute_bar");
        jdbcTemplate.update("DELETE FROM market_data_watermark");
        jdbcTemplate.update("DELETE FROM market_data_sync_task_item");
        jdbcTemplate.update("DELETE FROM market_data_sync_plan");
        jdbcTemplate.update("DELETE FROM market_calendar");
        jdbcTemplate.update("DELETE FROM market_trading_session");
        jdbcTemplate.update("DELETE FROM stock_basic");
    }

    /** 写入 CN_A 日历：给范围内日期打上交易日标记（2026-07-14~16 为周二~周四）。 */
    private void insertCalendar(String marketCode, String from, String to) {
        LocalDate date = LocalDate.parse(from);
        LocalDate end = LocalDate.parse(to);
        while (!date.isAfter(end)) {
            jdbcTemplate.update(
                    "INSERT INTO market_calendar (market_code, trade_date, is_trading_day) VALUES (?, ?, TRUE)",
                    marketCode, date);
            date = date.plusDays(1);
        }
    }

    @Test
    void availabilityWithoutBarsReturnsSecurityAndEmptyCombinations() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market, currency)
                VALUES ('US.AAPL', 'AAPL', 'Apple Inc.', 'US', 'USD')
                """);

        mockMvc.perform(get("/api/v1/market-data/assets/US.AAPL/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.security.canonicalSymbol").value("US.AAPL"))
                .andExpect(jsonPath("$.data.security.displayName").value("Apple Inc."))
                .andExpect(jsonPath("$.data.security.market").value("US"))
                .andExpect(jsonPath("$.data.security.currency").value("USD"))
                .andExpect(jsonPath("$.data.security.timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.data.combinations", hasSize(0)));
    }

    @Test
    void availabilityListsOnlyExistingCombinationsWithCoverageAndWatermark() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        // 日 K：LONGPORT/NONE 两条 + CSV/QF 一条
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-15', 'NONE', 'LONGPORT',
                    1450.00, 1455.00, 1448.00, 1453.00, 1000, 1453000.00, '2026-07-15 15:01:03')
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-16', 'NONE', 'LONGPORT',
                    1453.00, 1460.00, 1450.00, 1458.00, 1200, 1746000.00, '2026-07-16 15:02:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-14', 'QF', 'CSV',
                    1440.00, 1445.00, 1438.00, 1442.00, 800, 1153600.00, '2026-07-14 18:00:00')
                """);
        // 分钟 K：5M/LONGPORT/NONE 两条 + 水位
        jdbcTemplate.update("""
                INSERT INTO stock_minute_bar (canonical_symbol, trade_date, bar_start_time, bar_end_time,
                    interval_type, session_type, open_price, high_price, low_price, close_price,
                    volume, amount, adjust_type, data_source, fetched_at, quality_status)
                VALUES ('SH.600519', '2026-07-17', '2026-07-17 09:30:00', '2026-07-17 09:35:00',
                    '5M', 'AM', 1450.00, 1455.00, 1448.00, 1453.00, 1000, 1453000.00,
                    'NONE', 'LONGPORT', '2026-07-17 09:35:00', 'VALID')
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_minute_bar (canonical_symbol, trade_date, bar_start_time, bar_end_time,
                    interval_type, session_type, open_price, high_price, low_price, close_price,
                    volume, amount, adjust_type, data_source, fetched_at, quality_status)
                VALUES ('SH.600519', '2026-07-17', '2026-07-17 09:35:00', '2026-07-17 09:40:00',
                    '5M', 'AM', 1453.00, 1460.00, 1452.00, 1458.00, 800, 1166400.00,
                    'NONE', 'LONGPORT', '2026-07-17 09:40:00', 'VALID')
                """);
        jdbcTemplate.update("""
                INSERT INTO market_data_watermark (canonical_symbol, data_source, interval_type, adjust_type,
                    last_success_time, last_trade_date, last_bar_time, total_rows)
                VALUES ('SH.600519', 'LONGPORT', '5M', 'NONE',
                    '2026-07-17 09:41:00', '2026-07-17', '2026-07-17 09:40:00', 2)
                """);

        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.security.canonicalSymbol").value("SH.600519"))
                .andExpect(jsonPath("$.data.security.displayName").value("贵州茅台"))
                .andExpect(jsonPath("$.data.security.market").value("SH"))
                .andExpect(jsonPath("$.data.security.currency").value("CNY"))
                .andExpect(jsonPath("$.data.security.timeZone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.combinations", hasSize(3)))
                // 排序：interval -> dataSource -> adjustType
                .andExpect(jsonPath("$.data.combinations[0].interval").value("1D"))
                .andExpect(jsonPath("$.data.combinations[0].dataSource").value("CSV"))
                .andExpect(jsonPath("$.data.combinations[0].adjustType").value("QF"))
                .andExpect(jsonPath("$.data.combinations[0].barCount").value(1))
                .andExpect(jsonPath("$.data.combinations[0].firstBarTime").value("2026-07-14"))
                .andExpect(jsonPath("$.data.combinations[0].watermarkTime").doesNotExist())
                .andExpect(jsonPath("$.data.combinations[0].freshness").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.combinations[1].interval").value("1D"))
                .andExpect(jsonPath("$.data.combinations[1].dataSource").value("LONGPORT"))
                .andExpect(jsonPath("$.data.combinations[1].adjustType").value("NONE"))
                .andExpect(jsonPath("$.data.combinations[1].barCount").value(2))
                .andExpect(jsonPath("$.data.combinations[1].firstBarTime").value("2026-07-15"))
                .andExpect(jsonPath("$.data.combinations[1].lastBarTime").value("2026-07-16"))
                .andExpect(jsonPath("$.data.combinations[1].latestFetchedAt").value("2026-07-16T15:02:00+08:00"))
                .andExpect(jsonPath("$.data.combinations[1].freshness").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.combinations[2].interval").value("5M"))
                .andExpect(jsonPath("$.data.combinations[2].dataSource").value("LONGPORT"))
                .andExpect(jsonPath("$.data.combinations[2].adjustType").value("NONE"))
                .andExpect(jsonPath("$.data.combinations[2].barCount").value(2))
                .andExpect(jsonPath("$.data.combinations[2].firstBarTime").value("2026-07-17T09:30:00+08:00"))
                .andExpect(jsonPath("$.data.combinations[2].lastBarTime").value("2026-07-17T09:35:00+08:00"))
                .andExpect(jsonPath("$.data.combinations[2].latestFetchedAt").value("2026-07-17T09:40:00+08:00"))
                .andExpect(jsonPath("$.data.combinations[2].watermarkTime").value("2026-07-17T09:40:00+08:00"))
                .andExpect(jsonPath("$.data.combinations[2].freshness").value("UNKNOWN"));
    }

    @Test
    void availabilityForUnknownSecurityReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/market-data/assets/HK.99999/availability"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    void availabilityForInvalidSymbolReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/market-data/assets/XX.123/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_CANONICAL_SYMBOL"));
    }

    // ==================== series ====================

    @Test
    void seriesDailyReturnsVerifiedQualityWithSummary() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        insertCalendar("CN_A", "2026-07-14", "2026-07-16");
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-14', 'NONE', 'LONGPORT',
                    1440.00, 1445.00, 1438.00, 1442.00, 800, 1153600.00, '2026-07-14 18:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-15', 'NONE', 'LONGPORT',
                    1442.00, 1455.00, 1440.00, 1453.00, 1000, 1453000.00, '2026-07-15 15:01:03')
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('SH.600519', '2026-07-16', 'NONE', 'LONGPORT',
                    1453.00, 1460.00, 1450.00, 1458.00, 1200, 1746000.00, '2026-07-16 15:02:00')
                """);

        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "1D")
                        .param("from", "2026-07-14")
                        .param("to", "2026-07-16")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.security.canonicalSymbol").value("SH.600519"))
                .andExpect(jsonPath("$.data.security.currency").value("CNY"))
                .andExpect(jsonPath("$.data.query.interval").value("1D"))
                .andExpect(jsonPath("$.data.query.from").value("2026-07-14"))
                .andExpect(jsonPath("$.data.query.dataSource").value("LONGPORT"))
                .andExpect(jsonPath("$.data.availability.firstBarTime").value("2026-07-14"))
                .andExpect(jsonPath("$.data.availability.lastBarTime").value("2026-07-16"))
                .andExpect(jsonPath("$.data.availability.latestFetchedAt").value("2026-07-16T15:02:00+08:00"))
                .andExpect(jsonPath("$.data.availability.watermarkTime").doesNotExist())
                .andExpect(jsonPath("$.data.quality.coverageStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.quality.actualBarCount").value(3))
                .andExpect(jsonPath("$.data.quality.expectedBarCount").value(3))
                .andExpect(jsonPath("$.data.quality.missingBarCount").value(0))
                .andExpect(jsonPath("$.data.quality.suspectBarCount").value(0))
                .andExpect(jsonPath("$.data.quality.truncated").value(false))
                .andExpect(jsonPath("$.data.quality.reasonCodes", hasSize(0)))
                .andExpect(jsonPath("$.data.quality.freshness").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.quality.freshnessDetail").value("缺少权威交易日历，无法判定新鲜度"))
                .andExpect(jsonPath("$.data.summary.firstOpen").value("1440"))
                .andExpect(jsonPath("$.data.summary.lastClose").value("1458"))
                .andExpect(jsonPath("$.data.summary.absoluteChange").value("18"))
                .andExpect(jsonPath("$.data.summary.changeRate").value("0.0125"))
                .andExpect(jsonPath("$.data.summary.highestHigh").value("1460"))
                .andExpect(jsonPath("$.data.summary.lowestLow").value("1438"))
                .andExpect(jsonPath("$.data.summary.totalVolume").value(3000))
                .andExpect(jsonPath("$.data.summary.totalAmount").value("4352600"))
                .andExpect(jsonPath("$.data.summary.actualBarCount").value(3))
                .andExpect(jsonPath("$.data.bars", hasSize(3)))
                .andExpect(jsonPath("$.data.bars[0].time").value("2026-07-14"))
                .andExpect(jsonPath("$.data.bars[0].open").value("1440"))
                .andExpect(jsonPath("$.data.bars[2].close").value("1458"));
    }

    @Test
    void seriesMinuteReturnsPartialQualityWithSuspectAndWatermark() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        insertCalendar("CN_A", "2026-07-17", "2026-07-17");
        jdbcTemplate.update("""
                INSERT INTO stock_minute_bar (canonical_symbol, trade_date, bar_start_time, bar_end_time,
                    interval_type, session_type, open_price, high_price, low_price, close_price,
                    volume, amount, adjust_type, data_source, fetched_at, quality_status)
                VALUES ('SH.600519', '2026-07-17', '2026-07-17 09:30:00', '2026-07-17 09:35:00',
                    '5M', 'AM', 1450.00, 1455.00, 1448.00, 1453.00, 1000, 1453000.00,
                    'NONE', 'LONGPORT', '2026-07-17 09:35:00', 'VALID')
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_minute_bar (canonical_symbol, trade_date, bar_start_time, bar_end_time,
                    interval_type, session_type, open_price, high_price, low_price, close_price,
                    volume, amount, adjust_type, data_source, fetched_at, quality_status)
                VALUES ('SH.600519', '2026-07-17', '2026-07-17 09:35:00', '2026-07-17 09:40:00',
                    '5M', 'AM', 1453.00, 1460.00, 1452.00, 1458.00, 800, 1166400.00,
                    'NONE', 'LONGPORT', '2026-07-17 09:40:00', 'VALID')
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_minute_bar (canonical_symbol, trade_date, bar_start_time, bar_end_time,
                    interval_type, session_type, open_price, high_price, low_price, close_price,
                    volume, amount, adjust_type, data_source, fetched_at, quality_status)
                VALUES ('SH.600519', '2026-07-17', '2026-07-17 09:40:00', '2026-07-17 09:45:00',
                    '5M', 'AM', 1458.00, 1462.00, 1456.00, 1460.00, 600, 876000.00,
                    'NONE', 'LONGPORT', '2026-07-17 09:45:00', 'SUSPECT')
                """);
        jdbcTemplate.update("""
                INSERT INTO market_data_watermark (canonical_symbol, data_source, interval_type, adjust_type,
                    last_success_time, last_trade_date, last_bar_time, total_rows)
                VALUES ('SH.600519', 'LONGPORT', '5M', 'NONE',
                    '2026-07-17 09:46:00', '2026-07-17', '2026-07-17 09:40:00', 3)
                """);

        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "5M")
                        .param("from", "2026-07-17T09:30:00")
                        .param("to", "2026-07-17T11:30:00")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability.watermarkTime").value("2026-07-17T09:40:00+08:00"))
                .andExpect(jsonPath("$.data.quality.coverageStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data.quality.actualBarCount").value(3))
                .andExpect(jsonPath("$.data.quality.expectedBarCount").value(24))
                .andExpect(jsonPath("$.data.quality.missingBarCount").value(21))
                .andExpect(jsonPath("$.data.quality.suspectBarCount").value(1))
                .andExpect(jsonPath("$.data.quality.reasonCodes", hasItem("MISSING_BARS")))
                .andExpect(jsonPath("$.data.quality.reasonCodes", hasItem("SUSPECT_BARS")))
                .andExpect(jsonPath("$.data.quality.freshness").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.quality.freshnessDetail").value("缺少权威交易日历，无法判定新鲜度"))
                .andExpect(jsonPath("$.data.bars", hasSize(3)))
                .andExpect(jsonPath("$.data.bars[0].time").value("2026-07-17T09:30:00+08:00"))
                .andExpect(jsonPath("$.data.bars[0].qualityStatus").value("VALID"))
                .andExpect(jsonPath("$.data.bars[2].qualityStatus").value("SUSPECT"))
                .andExpect(jsonPath("$.data.summary.changeRate").value("0.0068965517"));
    }

    @Test
    void seriesForUnknownCombinationReturns400() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "1D")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MARKET_DATA_ASSET_COMBINATION_NOT_FOUND"));
    }

    @Test
    void seriesWithTooLargeRangeReturns400() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "1D")
                        .param("from", "2016-01-01")
                        .param("to", "2026-01-01")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MARKET_DATA_ASSET_RANGE_TOO_LARGE"));
    }

    @Test
    void seriesWithInvalidDateReturns400() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "1D")
                        .param("from", "not-a-date")
                        .param("to", "2026-07-31")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void seriesMissingRequiredParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARAM_ERROR"));
    }

    // ==================== 覆盖率：CN 分钟线部分区间（交集口径） ====================

    /** 批量插入 5M 分钟 bar：从 firstStart 起连续 count 根、每根 stepMinutes 分钟（bar 起点 + step 为终点）。 */
    private void insertMinuteBars(String symbol, LocalTime firstStart, int count, int stepMinutes) {
        for (int i = 0; i < count; i++) {
            LocalTime start = firstStart.plusMinutes((long) i * stepMinutes);
            LocalTime end = start.plusMinutes(stepMinutes);
            String startText = String.format("2026-07-17 %02d:%02d:00", start.getHour(), start.getMinute());
            String endText = String.format("2026-07-17 %02d:%02d:00", end.getHour(), end.getMinute());
            jdbcTemplate.update("""
                    INSERT INTO stock_minute_bar (canonical_symbol, trade_date, bar_start_time, bar_end_time,
                        interval_type, session_type, open_price, high_price, low_price, close_price,
                        volume, amount, adjust_type, data_source, fetched_at, quality_status)
                    VALUES (?, '2026-07-17', ?, ?, '5M', 'AM', 100.00, 101.00, 99.00, 100.50, 100, 10050.00,
                        'NONE', 'LONGPORT', ?, 'VALID')
                    """, symbol, startText, endText, endText);
        }
    }

    @Test
    void seriesMinuteMorningFullWindowIsVerified() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        insertCalendar("CN_A", "2026-07-17", "2026-07-17");
        // 10:00-10:55 共 12 根 5M bar，覆盖 10:00-11:00 完整部分区间
        insertMinuteBars("SH.600519", LocalTime.of(10, 0), 12, 5);

        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "5M")
                        .param("from", "2026-07-17T10:00:00")
                        .param("to", "2026-07-17T11:00:00")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quality.coverageStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.quality.actualBarCount").value(12))
                .andExpect(jsonPath("$.data.quality.expectedBarCount").value(12))
                .andExpect(jsonPath("$.data.quality.missingBarCount").value(0));
    }

    @Test
    void seriesMinuteLunchGapOnlyCountsSessionIntersection() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        insertCalendar("CN_A", "2026-07-17", "2026-07-17");
        // 上午 11:00-11:25 6 根 + 下午 13:00-13:25 6 根；午休 11:30-13:00 不产生 bar
        insertMinuteBars("SH.600519", LocalTime.of(11, 0), 6, 5);
        insertMinuteBars("SH.600519", LocalTime.of(13, 0), 6, 5);

        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "5M")
                        .param("from", "2026-07-17T11:00:00")
                        .param("to", "2026-07-17T13:30:00")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quality.coverageStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.quality.actualBarCount").value(12))
                .andExpect(jsonPath("$.data.quality.expectedBarCount").value(12))
                .andExpect(jsonPath("$.data.quality.missingBarCount").value(0));
    }

    @Test
    void seriesMinuteCrossDayCountsPartialSessions() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        insertCalendar("CN_A", "2026-07-16", "2026-07-17");
        // 仅 1 根 07-16 09:30；窗口 07-16 全天（48）+ 07-17 上午（24）= 72
        jdbcTemplate.update("""
                INSERT INTO stock_minute_bar (canonical_symbol, trade_date, bar_start_time, bar_end_time,
                    interval_type, session_type, open_price, high_price, low_price, close_price,
                    volume, amount, adjust_type, data_source, fetched_at, quality_status)
                VALUES ('SH.600519', '2026-07-16', '2026-07-16 09:30:00', '2026-07-16 09:35:00',
                    '5M', 'AM', 1450.00, 1455.00, 1448.00, 1453.00, 1000, 1453000.00,
                    'NONE', 'LONGPORT', '2026-07-16 09:35:00', 'VALID')
                """);

        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "5M")
                        .param("from", "2026-07-16T09:30:00")
                        .param("to", "2026-07-17T11:30:00")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quality.coverageStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data.quality.actualBarCount").value(1))
                .andExpect(jsonPath("$.data.quality.expectedBarCount").value(72))
                .andExpect(jsonPath("$.data.quality.missingBarCount").value(71))
                .andExpect(jsonPath("$.data.quality.reasonCodes", hasItem("MISSING_BARS")));
    }

    @Test
    void seriesMinuteZeroWidthRangeAtSessionOpenIsEmpty() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        insertCalendar("CN_A", "2026-07-17", "2026-07-17");
        insertMinuteBars("SH.600519", LocalTime.of(9, 30), 1, 5);

        // from == to == 会话开盘：零宽窗口，预期 0 根（第一根 bar 起点不落入集合）
        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/series")
                        .param("interval", "5M")
                        .param("from", "2026-07-17T09:30:00")
                        .param("to", "2026-07-17T09:30:00")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quality.coverageStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.quality.actualBarCount").value(1))
                .andExpect(jsonPath("$.data.quality.expectedBarCount").value(0))
                .andExpect(jsonPath("$.data.quality.missingBarCount").value(0));
    }

    @Test
    void seriesUsMarketReturnsUnknownQualityAndFreshness() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market, currency)
                VALUES ('US.AAPL', 'AAPL', 'Apple Inc.', 'US', 'USD')
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount, fetched_at)
                VALUES ('US.AAPL', '2026-07-17', 'NONE', 'LONGPORT',
                    1450.00, 1455.00, 1448.00, 1453.00, 1000, 1453000.00, '2026-07-17 15:01:03')
                """);

        mockMvc.perform(get("/api/v1/market-data/assets/US.AAPL/series")
                        .param("interval", "1D")
                        .param("from", "2026-07-17")
                        .param("to", "2026-07-17")
                        .param("adjustType", "NONE")
                        .param("dataSource", "LONGPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quality.coverageStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.quality.expectedBarCount").doesNotExist())
                .andExpect(jsonPath("$.data.quality.missingBarCount").doesNotExist())
                .andExpect(jsonPath("$.data.quality.freshness").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.quality.freshnessDetail").value("HK/US 日历未闭环，无法判定新鲜度"));
    }

    // ==================== related-tasks ====================

    @Test
    void relatedTasksListsPlansAndRunsForSymbol() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (canonical_symbol, symbol, name, market)
                VALUES ('SH.600519', '600519', '贵州茅台', 'SH')
                """);
        jdbcTemplate.update("""
                INSERT INTO market_data_sync_plan (plan_name, task_type, provider, scope_json, interval_type,
                    enabled, last_run_at)
                VALUES ('茅台5分钟采集', 'MINUTE_BAR', 'LONGPORT',
                    '{"canonicalSymbol":"SH.600519","startDate":"2026-07-01","endDate":"2026-07-31"}',
                    '5M', TRUE, '2026-07-17 09:41:00')
                """);
        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM market_data_sync_plan WHERE plan_name = '茅台5分钟采集'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO market_data_sync_task_item (task_id, plan_id, canonical_symbol, scope_detail, status,
                    started_at, finished_at)
                VALUES (100, ?, 'SH.600519',
                    '{"canonicalSymbol":"SH.600519","startDate":"2026-07-01","endDate":"2026-07-31"}',
                    'SUCCESS', '2026-07-17 09:35:00', '2026-07-17 09:41:00')
                """, planId);
        // 无关计划：不匹配 interval
        jdbcTemplate.update("""
                INSERT INTO market_data_sync_plan (plan_name, task_type, provider, scope_json, interval_type,
                    enabled, last_run_at)
                VALUES ('茅台日K采集', 'DAILY_BAR', 'LONGPORT',
                    '{"canonicalSymbol":"SH.600519","startDate":"2026-07-01","endDate":"2026-07-31"}',
                    '1D', TRUE, '2026-07-17 09:50:00')
                """);

        mockMvc.perform(get("/api/v1/market-data/assets/SH.600519/related-tasks")
                        .param("interval", "5M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.security.canonicalSymbol").value("SH.600519"))
                .andExpect(jsonPath("$.data.plans", hasSize(1)))
                .andExpect(jsonPath("$.data.plans[0].kind").value("PLAN"))
                .andExpect(jsonPath("$.data.plans[0].name").value("茅台5分钟采集"))
                .andExpect(jsonPath("$.data.plans[0].intervalType").value("5M"))
                .andExpect(jsonPath("$.data.plans[0].status").value("ENABLED"))
                .andExpect(jsonPath("$.data.plans[0].startDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.plans[0].endDate").value("2026-07-31"))
                .andExpect(jsonPath("$.data.plans[0].startedAt").value("2026-07-17T09:41:00+08:00"))
                .andExpect(jsonPath("$.data.runs", hasSize(1)))
                .andExpect(jsonPath("$.data.runs[0].kind").value("RUN"))
                .andExpect(jsonPath("$.data.runs[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.runs[0].intervalType").value("5M"))
                .andExpect(jsonPath("$.data.runs[0].startDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.runs[0].startedAt").value("2026-07-17T09:35:00+08:00"))
                .andExpect(jsonPath("$.data.runs[0].finishedAt").value("2026-07-17T09:41:00+08:00"));
    }
}
