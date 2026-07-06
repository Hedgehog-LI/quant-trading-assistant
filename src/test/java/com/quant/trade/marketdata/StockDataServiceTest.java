package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.dto.CreateStockBasicDTO;
import com.quant.trade.marketdata.dto.DailyBarQueryDTO;
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

/** 行情数据服务测试。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockDataServiceTest {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private StockDataManager stockDataManager;

    @Test
    void createStockSuccess() {
        StockBasicVO vo = stockDataService.createStock(new CreateStockBasicDTO(
            "600519", "SH", "贵州茅台", LocalDate.of(2001, 8, 27), false));
        assertNotNull(vo.id());
        assertEquals("SH.600519", vo.canonicalSymbol());
        assertEquals("贵州茅台", vo.name());
    }

    @Test
    void duplicateStockThrows() {
        stockDataService.createStock(new CreateStockBasicDTO("600519", "SH", "贵州茅台", null, false));
        assertThrows(BusinessException.class, () ->
            stockDataService.createStock(new CreateStockBasicDTO("600519", "SH", "茅台", null, false)));
    }

    @Test
    void invalidSymbolThrows() {
        assertThrows(BusinessException.class, () ->
            stockDataService.createStock(new CreateStockBasicDTO("abc", "SH", "测试", null, false)));
    }

    @Test
    void invalidMarketThrows() {
        assertThrows(BusinessException.class, () ->
            stockDataService.createStock(new CreateStockBasicDTO("600519", "XX", "测试", null, false)));
    }

    @Test
    void listAndQueryStocks() {
        stockDataService.createStock(new CreateStockBasicDTO("600519", "SH", "贵州茅台", null, false));
        stockDataService.createStock(new CreateStockBasicDTO("000001", "SZ", "平安银行", null, false));

        var all = stockDataService.listStocks(null, null);
        assertTrue(all.size() >= 2);

        var shStocks = stockDataService.listStocks("SH", null);
        assertTrue(shStocks.stream().anyMatch(s -> s.canonicalSymbol().equals("SH.600519")));

        var found = stockDataService.getStock("SH.600519");
        assertEquals("贵州茅台", found.name());
    }

    @Test
    void deleteStockSuccess() {
        stockDataService.createStock(new CreateStockBasicDTO("600519", "SH", "贵州茅台", null, false));
        stockDataService.deleteStock("SH.600519");
        assertThrows(BusinessException.class, () -> stockDataService.getStock("SH.600519"));
    }

    @Test
    void csvImportSuccess() {
        // 先创建股票
        stockDataService.createStock(new CreateStockBasicDTO("600519", "SH", "贵州茅台", null, false));

        String csv = "canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type\n" +
            "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n" +
            "SH.600519,2026-07-02,1690.00,1700.00,1685.00,1695.00,22000,37290000.00,NONE\n";

        DailyBarImportResultVO result = stockDataService.importDailyBars(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, result.inserted());
        assertEquals(0, result.failed());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void csvImportIdempotent() {
        stockDataService.createStock(new CreateStockBasicDTO("600519", "SH", "贵州茅台", null, false));
        String csv = "canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type\n" +
            "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";

        stockDataService.importDailyBars(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        DailyBarImportResultVO result2 = stockDataService.importDailyBars(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, result2.inserted());
        assertEquals(1, result2.skipped());
    }

    @Test
    void csvImportUnknownStockFails() {
        String csv = "canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type\n" +
            "SH.999999,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";

        DailyBarImportResultVO result = stockDataService.importDailyBars(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, result.inserted());
        assertEquals(1, result.failed());
        assertTrue(result.errors().get(0).message().contains("证券不存在"));
    }

    @Test
    void csvImportOhlcValidationFails() {
        stockDataService.createStock(new CreateStockBasicDTO("600519", "SH", "贵州茅台", null, false));
        // high < open（非法 OHLC）
        String csv = "canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type\n" +
            "SH.600519,2026-07-01,1700.00,1690.00,1680.00,1695.00,25000,42250000.00,NONE\n";

        DailyBarImportResultVO result = stockDataService.importDailyBars(
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, result.failed());
        assertTrue(result.errors().get(0).message().contains("high"));
    }

    @Test
    void queryDailyBarsAfterImport() {
        stockDataService.createStock(new CreateStockBasicDTO("600519", "SH", "贵州茅台", null, false));
        String csv = "canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type\n" +
            "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n" +
            "SH.600519,2026-07-02,1690.00,1700.00,1685.00,1695.00,22000,37290000.00,NONE\n";

        stockDataService.importDailyBars(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        var bars = stockDataService.queryDailyBars(
            new DailyBarQueryDTO("SH.600519", null, null, null));
        assertEquals(2, bars.size());
    }

    @Test
    void canonicalSymbolNormalization() {
        assertEquals("SH.600519", stockDataManager.buildCanonicalSymbol("sh", "600519"));
        assertEquals("SZ.000001", stockDataManager.buildCanonicalSymbol("SZ", "000001"));
        assertEquals("BJ.430047", stockDataManager.buildCanonicalSymbol("bj", "430047"));
    }
}
