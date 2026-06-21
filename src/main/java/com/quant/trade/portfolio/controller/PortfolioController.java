package com.quant.trade.portfolio.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.PortfolioConstants;
import com.quant.trade.portfolio.dto.PriceSnapshotDTO;
import com.quant.trade.portfolio.service.PortfolioService;
import com.quant.trade.portfolio.vo.ClosedTradeVO;
import com.quant.trade.portfolio.vo.PortfolioSummaryVO;
import com.quant.trade.portfolio.vo.PositionVO;
import com.quant.trade.portfolio.vo.PriceSnapshotVO;
import com.quant.trade.portfolio.vo.SymbolDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 持仓账本 REST 控制器。
 * <p>
 * 提供汇总统计、当前持仓、已结算交易、单股票详情、手工当前价维护等接口。
 * 所有结果仅用于复盘参考，不构成投资建议。
 */
@RestController
@RequestMapping(PortfolioConstants.PATH_PREFIX)
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/summary")
    public ApiResponse<PortfolioSummaryVO> summary() {
        return ApiResponse.ok(portfolioService.getSummary());
    }

    @GetMapping("/positions")
    public ApiResponse<List<PositionVO>> positions() {
        return ApiResponse.ok(portfolioService.getPositions());
    }

    @GetMapping("/closed-trades")
    public ApiResponse<List<ClosedTradeVO>> closedTrades(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {
        return ApiResponse.ok(portfolioService.getClosedTrades(symbol, fromDate, toDate));
    }

    @GetMapping("/symbol/{symbol}")
    public ApiResponse<SymbolDetailVO> symbolDetail(@PathVariable String symbol) {
        return ApiResponse.ok(portfolioService.getSymbolDetail(symbol));
    }

    @PostMapping("/prices")
    public ApiResponse<PriceSnapshotVO> upsertPrice(@Valid @RequestBody PriceSnapshotDTO dto) {
        return ApiResponse.ok(portfolioService.upsertPrice(dto));
    }

    @GetMapping("/prices")
    public ApiResponse<List<PriceSnapshotVO>> listPrices() {
        return ApiResponse.ok(portfolioService.listPrices());
    }
}
