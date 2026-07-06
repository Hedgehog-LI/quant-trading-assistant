package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.dto.CreateStockBasicDTO;
import com.quant.trade.marketdata.dto.DailyBarQueryDTO;
import com.quant.trade.marketdata.service.StockDataService;
import com.quant.trade.marketdata.vo.DailyBarImportResultVO;
import com.quant.trade.marketdata.vo.StockBasicVO;
import com.quant.trade.marketdata.vo.StockDailyBarVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** 行情数据 REST 控制器。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data")
@RequiredArgsConstructor
public class StockDataController {

    private final StockDataService stockDataService;

    // ===== 证券主数据 =====

    @GetMapping("/stocks")
    public ApiResponse<List<StockBasicVO>> listStocks(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(stockDataService.listStocks(market, keyword));
    }

    @PostMapping("/stocks")
    public ApiResponse<StockBasicVO> createStock(@Valid @RequestBody CreateStockBasicDTO dto) {
        return ApiResponse.ok(stockDataService.createStock(dto));
    }

    @GetMapping("/stocks/{canonicalSymbol}")
    public ApiResponse<StockBasicVO> getStock(@PathVariable String canonicalSymbol) {
        return ApiResponse.ok(stockDataService.getStock(canonicalSymbol));
    }

    @PutMapping("/stocks/{id}")
    public ApiResponse<StockBasicVO> updateStock(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) LocalDate listDate,
            @RequestParam(required = false) Boolean delisted) {
        return ApiResponse.ok(stockDataService.updateStock(id, name, listDate, delisted));
    }

    @DeleteMapping("/stocks/{canonicalSymbol}")
    public ApiResponse<Void> deleteStock(@PathVariable String canonicalSymbol) {
        stockDataService.deleteStock(canonicalSymbol);
        return ApiResponse.ok();
    }

    // ===== 日 K 数据 =====

    @GetMapping("/daily-bars")
    public ApiResponse<List<StockDailyBarVO>> queryDailyBars(
            @RequestParam(required = false) String canonicalSymbol,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String adjustType) {
        return ApiResponse.ok(stockDataService.queryDailyBars(
                new DailyBarQueryDTO(canonicalSymbol, fromDate, toDate, adjustType)));
    }

    @PostMapping("/daily-bars/import")
    public ApiResponse<DailyBarImportResultVO> importDailyBars(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ApiResponse.ok(stockDataService.importDailyBars(file.getInputStream()));
    }

    @GetMapping("/daily-bars/template")
    public ApiResponse<String> downloadTemplate() {
        return ApiResponse.ok("canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type\n" +
                "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n");
    }
}
