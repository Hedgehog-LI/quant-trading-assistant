package com.quant.trade.review.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.review.dto.CreateReviewDTO;
import com.quant.trade.review.dto.UpdateReviewDTO;
import com.quant.trade.review.service.ReviewService;
import com.quant.trade.review.vo.ReviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 盘后复盘管理接口。
 * <p>
 * 提供复盘记录的增删改查，支持关联交易记录自动标记已复盘。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ApiResponse<ReviewVO> create(@Valid @RequestBody CreateReviewDTO dto) {
        return ApiResponse.ok(reviewService.create(dto));
    }

    @GetMapping
    public ApiResponse<List<ReviewVO>> list(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String symbol) {
        return ApiResponse.ok(reviewService.list(date, symbol));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReviewVO> update(@PathVariable Long id,
                                         @Valid @RequestBody UpdateReviewDTO dto) {
        return ApiResponse.ok(reviewService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        reviewService.delete(id);
        return ApiResponse.ok();
    }
}
