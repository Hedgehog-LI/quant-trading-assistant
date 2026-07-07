package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.dto.CreateStockBasicDTO;
import com.quant.trade.marketdata.dto.DailyBarQueryDTO;
import com.quant.trade.marketdata.vo.PageResultVO;
import com.quant.trade.marketdata.dto.UpdateStockBasicDTO;
import com.quant.trade.marketdata.manager.StockDataManager;
import com.quant.trade.marketdata.service.StockDataService;
import com.quant.trade.marketdata.vo.DailyBarImportResultVO;
import com.quant.trade.marketdata.vo.StockBasicVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** 行情数据服务完整测试（含 CSV 重复键、冲突、表头校验、删除保护、分页、编辑）。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockDataServiceTest {

    @Autowired private StockDataService stockDataService;
    @Autowired private StockDataManager stockDataManager;

    // ===== canonical 规范化 =====

    @Test
    void canonicalSymbolNormalization() {
        assertEquals("SH.600519", stockDataManager.buildCanonicalSymbol("sh", "600519"));
        assertEquals("SZ.000001", stockDataManager.buildCanonicalSymbol("SZ", "000001"));
        assertEquals("BJ.430047", stockDataManager.buildCanonicalSymbol("bj", "430047"));
    }

    @Test
    void invalidSymbolThrows() {
        assertThrows(BusinessException.class, () ->
            stockDataManager.buildCanonicalSymbol("SH", "abc"));
    }

    @Test
    void invalidMarketThrows() {
        assertThrows(BusinessException.class, () ->
            stockDataManager.buildCanonicalSymbol("XX", "600519"));
    }

    // ===== CRUD =====

    @Test
    void createStockSuccess() {
        StockBasicVO vo = createStock("600519", "SH", "贵州茅台");
        assertNotNull(vo.id());
        assertEquals("SH.600519", vo.canonicalSymbol());
    }

    @Test
    void duplicateStockThrows() {
        createStock("600519", "SH", "贵州茅台");
        assertThrows(BusinessException.class, () ->
            createStock("600519", "SH", "茅台"));
    }

    @Test
    void updateStockSuccess() {
        StockBasicVO vo = createStock("600519", "SH", "贵州茅台");
        StockBasicVO updated = stockDataService.updateStock(vo.id(),
                new UpdateStockBasicDTO("茅台集团", LocalDate.of(2001, 8, 27), false));
        assertEquals("茅台集团", updated.name());
        assertEquals(LocalDate.of(2001, 8, 27), updated.listDate());
    }

    @Test
    void deleteStockWithoutBarsSuccess() {
        createStock("600519", "SH", "贵州茅台");
        stockDataService.deleteStock("SH.600519");
        assertThrows(BusinessException.class, () -> stockDataService.getStock("SH.600519"));
    }

    @Test
    void deleteStockWithBarsRejected() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() + "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";
        stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stockDataService.deleteStock("SH.600519"));
        assertEquals("STOCK_HAS_DAILY_BARS", ex.getErrorCode().getCode());
    }

    // ===== 分页 =====

    @Test
    void listStocksPagination() {
        createStock("600519", "SH", "贵州茅台");
        createStock("000001", "SZ", "平安银行");
        createStock("430047", "BJ", "诺思兰德");

        PageResultVO<StockBasicVO> page1 = stockDataService.listStocks(null, null, 1, 2);
        assertEquals(3L, page1.total());
        assertEquals(2, page1.items().size());

        PageResultVO<StockBasicVO> page2 = stockDataService.listStocks(null, null, 2, 2);
        assertEquals(1, page2.items().size());
    }

    @Test
    void listStocksMarketFilter() {
        createStock("600519", "SH", "贵州茅台");
        createStock("000001", "SZ", "平安银行");
        PageResultVO<StockBasicVO> result = stockDataService.listStocks("SH", null, 1, 20);
        assertEquals(1L, result.total());
    }

    @Test
    void dailyBarsPagination() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() +
            "SH.600519,2026-07-01,1680,1695,1678,1690,25000,42250000,NONE\n" +
            "SH.600519,2026-07-02,1690,1700,1685,1695,22000,37290000,NONE\n" +
            "SH.600519,2026-07-03,1695,1710,1690,1705,18000,30690000,NONE\n";
        stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);

        PageResultVO<com.quant.trade.marketdata.vo.StockDailyBarVO> page1 = stockDataService.queryDailyBars(
                new DailyBarQueryDTO("SH.600519", null, null, null, null), 1, 2);
        assertEquals(3L, page1.total());
        assertEquals(2, page1.items().size());
    }

    // ===== CSV 导入 =====

    @Test
    void csvImportSuccess() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() +
            "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n" +
            "SH.600519,2026-07-02,1690.00,1700.00,1685.00,1695.00,22000,37290000.00,NONE\n";
        DailyBarImportResultVO result = stockDataService.importDailyBars(
                stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(2, result.inserted());
        assertEquals(0, result.failed());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void csvImportIdempotentSameContent() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() + "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";
        stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        DailyBarImportResultVO result2 = stockDataService.importDailyBars(
                stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(0, result2.inserted());
        assertEquals(1, result2.skipped());
    }

    @Test
    void csvImportUpdateDifferentContent() {
        createStock("600519", "SH", "贵州茅台");
        String csv1 = header() + "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";
        stockDataService.importDailyBars(stream(csv1), csv1.getBytes(StandardCharsets.UTF_8).length);
        String csv2 = header() + "SH.600519,2026-07-01,1690.00,1700.00,1685.00,1695.00,22000,37290000.00,NONE\n";
        DailyBarImportResultVO result2 = stockDataService.importDailyBars(
                stream(csv2), csv2.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(0, result2.inserted());
        assertEquals(1, result2.updated());
    }

    @Test
    void csvImportSameFileDuplicateSameContentSkipped() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() +
            "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n" +
            "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";
        DailyBarImportResultVO result = stockDataService.importDailyBars(
                stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(1, result.inserted());
        assertEquals(1, result.skipped());
        assertEquals(0, result.failed());
    }

    @Test
    void csvImportSameFileDuplicateConflictRejected() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() +
            "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n" +
            "SH.600519,2026-07-01,9999.00,9999.00,9999.00,9999.00,99999,999999999.00,NONE\n";
        DailyBarImportResultVO result = stockDataService.importDailyBars(
                stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(0, result.inserted());
        assertEquals(1, result.failed());
        assertTrue(result.errors().get(0).message().contains("冲突"));
    }

    @Test
    void csvImportUnknownStockFails() {
        String csv = header() + "SH.999999,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";
        DailyBarImportResultVO result = stockDataService.importDailyBars(
                stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(1, result.failed());
        assertTrue(result.errors().get(0).message().contains("证券不存在"));
    }

    @Test
    void csvImportOhlcValidationFails() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() + "SH.600519,2026-07-01,1700.00,1690.00,1680.00,1695.00,25000,42250000.00,NONE\n";
        DailyBarImportResultVO result = stockDataService.importDailyBars(
                stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(1, result.failed());
        assertTrue(result.errors().get(0).message().contains("high"));
    }

    @Test
    void csvImportEmptyFileThrows() {
        assertThrows(BusinessException.class, () ->
            stockDataService.importDailyBars(stream(""), 0L));
    }

    @Test
    void csvImportWrongHeaderThrows() {
        createStock("600519", "SH", "贵州茅台");
        String csv = "foo,bar\nSH.600519,2026-07-01\n";
        assertThrows(BusinessException.class, () ->
            stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void csvImportQueryAfterImport() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() +
            "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n" +
            "SH.600519,2026-07-02,1690.00,1700.00,1685.00,1695.00,22000,37290000.00,NONE\n";
        stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        PageResultVO<com.quant.trade.marketdata.vo.StockDailyBarVO> result = stockDataService.queryDailyBars(
                new DailyBarQueryDTO("SH.600519", null, null, null, null), 1, 20);
        assertEquals(2L, result.total());
    }

    @Test
    void csvImportDataSourceFilter() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() + "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";
        stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        PageResultVO<com.quant.trade.marketdata.vo.StockDailyBarVO> filterResult = stockDataService.queryDailyBars(
                new DailyBarQueryDTO("SH.600519", null, null, null, "CSV"), 1, 20);
        assertEquals(1L, filterResult.total());
        filterResult = stockDataService.queryDailyBars(
                new DailyBarQueryDTO("SH.600519", null, null, null, "MANUAL"), 1, 20);
        assertEquals(0L, filterResult.total());
    }

    // ===== 分页参数校验 =====

    @Test
    void listStocksInvalidPageThrows() {
        assertThrows(BusinessException.class,
                () -> stockDataService.listStocks(null, null, 0, 10));
    }

    @Test
    void listStocksInvalidSizeZeroThrows() {
        assertThrows(BusinessException.class,
                () -> stockDataService.listStocks(null, null, 1, 0));
    }

    @Test
    void listStocksInvalidSizeTooLargeThrows() {
        assertThrows(BusinessException.class,
                () -> stockDataService.listStocks(null, null, 1, 501));
    }

    @Test
    void queryDailyBarsInvalidPageThrows() {
        assertThrows(BusinessException.class,
                () -> stockDataService.queryDailyBars(new DailyBarQueryDTO(null, null, null, null, null), -1, 10));
    }

    // ===== CSV 表头严格校验 =====

    @Test
    void csvImportExtraColumnHeaderThrows() {
        createStock("600519", "SH", "贵州茅台");
        String csv = "canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type,extra\n" +
                "SH.600519,2026-07-01,1680,1695,1678,1690,25000,42250000,NONE,x\n";
        assertThrows(BusinessException.class, () ->
                stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void csvImportMissingColumnHeaderThrows() {
        createStock("600519", "SH", "贵州茅台");
        String csv = "canonical_symbol,trade_date,open,high,low,close,volume\n" +
                "SH.600519,2026-07-01,1680,1695,1678,1690,25000\n";
        assertThrows(BusinessException.class, () ->
                stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void csvImportWrongColumnOrderThrows() {
        createStock("600519", "SH", "贵州茅台");
        String csv = "trade_date,canonical_symbol,open,high,low,close,volume,amount,adjust_type\n" +
                "2026-07-01,SH.600519,1680,1695,1678,1690,25000,42250000,NONE\n";
        assertThrows(BusinessException.class, () ->
                stockDataService.importDailyBars(stream(csv), csv.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void csvImportUtf8BomSuccess() {
        createStock("600519", "SH", "贵州茅台");
        // UTF-8 BOM + 正常 CSV
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] csvBytes = (header() + "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(csvBytes, 0, combined, bom.length, csvBytes.length);
        DailyBarImportResultVO result = stockDataService.importDailyBars(
                new ByteArrayInputStream(combined), combined.length);
        assertEquals(1, result.inserted());
        assertEquals(0, result.failed());
    }

    @Test
    void csvImportInvalidDateThrows() {
        createStock("600519", "SH", "贵州茅台");
        String csv = header() + "SH.600519,2026-13-45,1680,1695,1678,1690,25000,42250000,NONE\n";
        DailyBarImportResultVO result = stockDataService.importDailyBars(
                stream(csv), csv.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(1, result.failed());
    }

    // ===== helpers =====

    private StockBasicVO createStock(String symbol, String market, String name) {
        return stockDataService.createStock(new CreateStockBasicDTO(symbol, market, name, null, false));
    }

    private static String header() {
        return "canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type\n";
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
