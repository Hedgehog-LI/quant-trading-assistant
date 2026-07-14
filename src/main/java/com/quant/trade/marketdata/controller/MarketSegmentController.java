package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dto.SegmentDTO;
import com.quant.trade.marketdata.dto.SegmentMemberDTO;
import com.quant.trade.marketdata.service.MarketSegmentService;
import com.quant.trade.marketdata.vo.MarketSegmentMemberVO;
import com.quant.trade.marketdata.vo.MarketSegmentVO;
import com.quant.trade.marketdata.vo.PageResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 板块/自定义分组 REST 控制器。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data/segments")
@RequiredArgsConstructor
public class MarketSegmentController {

    private final MarketSegmentService segmentService;

    @PostMapping
    public ApiResponse<MarketSegmentVO> create(@Valid @RequestBody SegmentDTO dto) {
        return ApiResponse.ok(segmentService.createSegment(dto));
    }

    @GetMapping
    public ApiResponse<PageResultVO<MarketSegmentVO>> list(
            @RequestParam(required = false) String segmentType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(segmentService.listSegments(segmentType, enabled, keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<MarketSegmentVO> get(@PathVariable Long id) {
        return ApiResponse.ok(segmentService.getSegment(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MarketSegmentVO> update(@PathVariable Long id, @Valid @RequestBody SegmentDTO dto) {
        return ApiResponse.ok(segmentService.updateSegment(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        segmentService.deleteSegment(id);
        return ApiResponse.ok(null);
    }

    // ==================== 成员 ====================

    @GetMapping("/{id}/members")
    public ApiResponse<List<MarketSegmentMemberVO>> listMembers(@PathVariable Long id) {
        return ApiResponse.ok(segmentService.listMembers(id));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<MarketSegmentMemberVO> addMember(@PathVariable Long id,
                                                         @Valid @RequestBody SegmentMemberDTO dto) {
        return ApiResponse.ok(segmentService.addMember(id, dto));
    }

    @DeleteMapping("/{id}/members/{canonicalSymbol}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable String canonicalSymbol) {
        segmentService.removeMember(id, canonicalSymbol);
        return ApiResponse.ok(null);
    }
}
