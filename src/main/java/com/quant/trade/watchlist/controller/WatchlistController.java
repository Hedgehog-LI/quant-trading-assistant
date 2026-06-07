package com.quant.trade.watchlist.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.watchlist.dto.CreateWatchlistDTO;
import com.quant.trade.watchlist.dto.UpdateEnabledDTO;
import com.quant.trade.watchlist.dto.UpdateWatchlistDTO;
import com.quant.trade.watchlist.service.WatchlistService;
import com.quant.trade.watchlist.vo.WatchlistVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自选股管理接口。
 * <p>
 * 提供自选股的增删改查、启用停用、按条件筛选等功能。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @PostMapping
    public ApiResponse<WatchlistVO> create(@Valid @RequestBody CreateWatchlistDTO dto) {
        return ApiResponse.ok(watchlistService.create(dto));
    }

    @GetMapping
    public ApiResponse<List<WatchlistVO>> list(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tradeStyle) {
        return ApiResponse.ok(watchlistService.list(enabled, keyword, tradeStyle));
    }

    @GetMapping("/{id}")
    public ApiResponse<WatchlistVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(watchlistService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<WatchlistVO> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateWatchlistDTO dto) {
        return ApiResponse.ok(watchlistService.update(id, dto));
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<WatchlistVO> updateEnabled(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateEnabledDTO dto) {
        return ApiResponse.ok(watchlistService.updateEnabled(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        watchlistService.delete(id);
        return ApiResponse.ok();
    }
}
