package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dto.CreateStockBasicDTO;
import com.quant.trade.marketdata.dto.DailyBarQueryDTO;
import com.quant.trade.marketdata.dto.UpdateStockBasicDTO;
import com.quant.trade.marketdata.service.StockDataService;
import com.quant.trade.marketdata.vo.DailyBarImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/** 行情数据 REST 控制器。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data")
@RequiredArgsConstructor
public class StockDataController {

    private final StockDataService stockDataService;

    // ===== 证券主数据 =====

    @GetMapping("/stocks")
    public ApiResponse<Map<String, Object>> listStocks(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        if (size > MarketDataConstants.MAX_PAGE_SIZE) size = MarketDataConstants.MAX_PAGE_SIZE;
        return ApiResponse.ok(stockDataService.listStocks(market, keyword, page, size));
    }

    @PostMapping("/stocks")
    public ApiResponse<Object> createStock(@Valid @RequestBody CreateStockBasicDTO dto) {
        return ApiResponse.ok(stockDataService.createStock(dto));
    }

    @GetMapping("/stocks/{canonicalSymbol}")
    public ApiResponse<Object> getStock(@PathVariable String canonicalSymbol) {
        return ApiResponse.ok(stockDataService.getStock(canonicalSymbol));
    }

    @PutMapping("/stocks/{id}")
    public ApiResponse<Object> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockBasicDTO dto) {
        return ApiResponse.ok(stockDataService.updateStock(id, dto));
    }

    @DeleteMapping("/stocks/{canonicalSymbol}")
    public ApiResponse<Void> deleteStock(@PathVariable String canonicalSymbol) {
        stockDataService.deleteStock(canonicalSymbol);
        return ApiResponse.ok();
    }

    // ===== 日 K 数据 =====

    @GetMapping("/daily-bars")
    public ApiResponse<Map<String, Object>> queryDailyBars(
            @RequestParam(required = false) String canonicalSymbol,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String adjustType,
            @RequestParam(required = false) String dataSource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        if (size > MarketDataConstants.MAX_PAGE_SIZE) size = MarketDataConstants.MAX_PAGE_SIZE;
        return ApiResponse.ok(stockDataService.queryDailyBars(
                new DailyBarQueryDTO(canonicalSymbol, fromDate, toDate, adjustType, dataSource), page, size));
    }

    @PostMapping("/daily-bars/import")
    public ApiResponse<DailyBarImportResultVO> importDailyBars(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ApiResponse.ok(stockDataService.importDailyBars(file.getInputStream(), file.getSize()));
    }

    @GetMapping("/daily-bars/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=daily-bar-template.csv");
        response.getWriter().write(MarketDataConstants.CSV_TEMPLATE);
        response.getWriter().flush();
    }
}
