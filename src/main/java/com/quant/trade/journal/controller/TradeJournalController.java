package com.quant.trade.journal.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.dto.UpdateReviewStatusDTO;
import com.quant.trade.journal.dto.UpdateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.journal.vo.TradeJournalVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易记录管理接口。
 * <p>
 * 提供手工交易记录的增删改查和复盘状态管理。
 * 交易记录为手工录入，不连接券商。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/trade-journals")
@RequiredArgsConstructor
public class TradeJournalController {

    private final TradeJournalService tradeJournalService;

    @PostMapping
    public ApiResponse<TradeJournalVO> create(@Valid @RequestBody CreateTradeJournalDTO dto) {
        return ApiResponse.ok(tradeJournalService.create(dto));
    }

    @GetMapping
    public ApiResponse<List<TradeJournalVO>> list(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String reviewStatus) {
        return ApiResponse.ok(tradeJournalService.list(date, symbol, reviewStatus));
    }

    @GetMapping("/{id}")
    public ApiResponse<TradeJournalVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(tradeJournalService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TradeJournalVO> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateTradeJournalDTO dto) {
        return ApiResponse.ok(tradeJournalService.update(id, dto));
    }

    @PatchMapping("/{id}/review-status")
    public ApiResponse<TradeJournalVO> updateReviewStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewStatusDTO dto) {
        return ApiResponse.ok(tradeJournalService.updateReviewStatus(id, dto));
    }
}
