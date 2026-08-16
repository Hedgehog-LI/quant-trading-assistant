package com.quant.trade.marketdata.foundation;

import com.quant.trade.marketdata.foundation.model.MdfBackfillChunkDO;
import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import com.quant.trade.marketdata.foundation.provider.TencentPublicHistoricalBarProvider;
import com.quant.trade.marketdata.foundation.service.DataBackfillService;
import com.quant.trade.marketdata.foundation.service.DataFoundationDatasetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1 §一：全 A 二维分片（5000+ 证券 × 2021 至今，纯 stub 规划，不外联不执行）。
 * 证券范围入 mdf_backfill_task_symbol（不塞 symbols_json）；chunk=(证券组×日期窗)；
 * 每窗 ≤ Provider 安全窗（腾讯 365 天 << 640 条截断上限）；chunk.start/end=实际请求区间。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(StubHistoricalBarProviderConfig.class)
class BackfillScaleChunkingTest {

    private static final LocalDate START = LocalDate.of(2021, 1, 4);
    private static final LocalDate END = LocalDate.now();

    @Autowired
    private DataFoundationDatasetService datasetService;
    @Autowired
    private DataBackfillService backfillService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanFoundationTables() {
        FoundationTestTables.cleanAll(jdbcTemplate);
    }

    @Test
    void fiveThousandSymbolsFullWindowPlanAsSymbolGroupsTimesDateWindows() {
        datasetService.createDataset("SCALE_DS", "全A规模数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "规模测试");

        List<String> symbols = IntStream.range(0, 5400)
                .mapToObj(i -> String.format("SH.6%05d", 10000 + i)).toList();
        MdfBackfillTaskDO task = backfillService.createTask("SCALE_DS", "CN",
                StubHistoricalBarProvider.PROVIDER_CODE, "1D", "NONE", START, END, symbols, 500);

        assertEquals(5400, task.getPlannedCount(), "全 A 股票池规模可直接创建");
        Long symbolRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mdf_backfill_task_symbol WHERE task_id = ?", Long.class, task.getId());
        assertEquals(5400L, symbolRows, "证券范围规范化入表（不塞 symbols_json）");
        assertTrue(task.getSymbolsJson() == null || task.getSymbolsJson().isBlank(),
                "全量 symbols 不得写入 symbols_json");

        List<MdfBackfillChunkDO> chunks = backfillService.listChunks(task.getId());
        long expectedWindows = DataBackfillService.splitWindows(START, END,
                StubHistoricalBarProvider.INSTANCE_SAFE_WINDOW).size();
        int expectedGroups = (5400 + 499) / 500;
        assertEquals(expectedGroups * expectedWindows, chunks.size(), "chunk 数=证券组×日期窗");
        assertTrue(chunks.size() <= 40000, "分片总数在输入保护上限内");

        // 每片日期=实际请求区间；同一证券组的所有日期窗拼接覆盖全窗口无重叠无空洞
        for (MdfBackfillChunkDO chunk : chunks) {
            long days = ChronoUnit.DAYS.between(chunk.getStartDate(), chunk.getEndDate()) + 1;
            assertTrue(days <= StubHistoricalBarProvider.INSTANCE_SAFE_WINDOW,
                    "单窗不得超 Provider 安全窗（防 640 截断）");
            assertTrue(!chunk.getStartDate().isBefore(START) && !chunk.getEndDate().isAfter(END),
                    "chunk 日期必须在任务窗口内");
        }
        long distinctWindows = chunks.stream()
                .map(chunk -> chunk.getStartDate() + ".." + chunk.getEndDate()).distinct().count();
        assertEquals(expectedWindows, distinctWindows);
        // 规划不执行：全部 PENDING、零写入
        assertTrue(chunks.stream().allMatch(chunk -> "PENDING".equals(chunk.getStatus())));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_daily_bar", Long.class));
    }

    @Test
    void tencentSafeWindowPrevents640BarTruncation() {
        // 腾讯端点单次约 640 条；安全窗 365 自然日 ≈ 250 交易日 << 640
        assertEquals(365, TencentPublicHistoricalBarProvider.SAFE_REQUEST_WINDOW_DAYS);
        List<DataBackfillService.DateWindow> windows = DataBackfillService.splitWindows(
                LocalDate.of(2021, 1, 1), LocalDate.of(2026, 8, 16), 365);
        assertEquals(6, windows.size(), "2021-01-01 至 2026-08-16 按 365 天切 6 窗");
        assertEquals(LocalDate.of(2021, 1, 1), windows.get(0).start());
        assertEquals(LocalDate.of(2021, 12, 31), windows.get(0).end());
        assertEquals(LocalDate.of(2026, 8, 16), windows.get(5).end(), "末窗裁剪到窗口终点");
        for (DataBackfillService.DateWindow window : windows) {
            assertTrue(ChronoUnit.DAYS.between(window.start(), window.end()) + 1 <= 365);
        }
        // 相邻窗连续（左闭右闭 + plusDays(1)）
        for (int i = 1; i < windows.size(); i++) {
            assertEquals(windows.get(i - 1).end().plusDays(1), windows.get(i).start());
        }
    }
}
