package com.quant.trade.portfolio.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.PositionSnapshotConstants;
import com.quant.trade.portfolio.dto.CreatePositionSnapshotDTO;
import com.quant.trade.portfolio.dto.UpdatePositionSnapshotDTO;
import com.quant.trade.portfolio.service.PositionSnapshotService;
import com.quant.trade.portfolio.vo.PositionSnapshotComparisonVO;
import com.quant.trade.portfolio.vo.PositionSnapshotDetailVO;
import com.quant.trade.portfolio.vo.PositionSnapshotReconciliationVO;
import com.quant.trade.portfolio.vo.PositionSnapshotSummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 持仓快照 REST 控制器。
 * <p>
 * 提供手工持仓盘点的草稿保存、确认、作废、历史查询和详情查询能力。
 * 本模块不连接券商，也不会根据快照自动生成交易流水。
 */
@RestController
@RequestMapping(PositionSnapshotConstants.PATH_PREFIX)
@RequiredArgsConstructor
public class PositionSnapshotController {

    private final PositionSnapshotService positionSnapshotService;

    /** 新建持仓快照草稿或已确认快照。 */
    @PostMapping
    public ApiResponse<PositionSnapshotDetailVO> create(
            @Valid @RequestBody CreatePositionSnapshotDTO dto) {
        return ApiResponse.ok(positionSnapshotService.create(dto));
    }

    /**
     * 查询历史快照。默认隐藏已作废记录；显式指定 status=CANCELED 时仍可查询作废记录。
     */
    @GetMapping
    public ApiResponse<List<PositionSnapshotSummaryVO>> list(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "false") boolean includeCanceled) {
        return ApiResponse.ok(positionSnapshotService.list(
                fromDate, toDate, status, sourceType, includeCanceled));
    }

    /** 查询最新一条已确认快照；不存在时 data 为空。 */
    @GetMapping("/latest")
    public ApiResponse<PositionSnapshotDetailVO> latest() {
        return ApiResponse.ok(positionSnapshotService.getLatestConfirmed());
    }

    /**
     * 对比两个已确认持仓快照的差异。
     * <p>
     * 仅支持 {@code CONFIRMED} 快照，且基准快照时间必须严格早于目标快照时间。
     * 结果仅用于复盘参考，不构成投资建议。
     */
    @GetMapping("/comparison")
    public ApiResponse<PositionSnapshotComparisonVO> compare(
            @RequestParam Long baseSnapshotId,
            @RequestParam Long targetSnapshotId) {
        return ApiResponse.ok(positionSnapshotService.compare(baseSnapshotId, targetSnapshotId));
    }

    /**
     * 已确认快照与截止时点 FIFO 账本对账。
     * <p>
     * 以数量为核心一致性判断；结果只读，不会自动修改交易流水，也不构成投资建议。
     */
    @GetMapping("/{snapshotId}/reconciliation")
    public ApiResponse<PositionSnapshotReconciliationVO> reconcile(
            @PathVariable Long snapshotId) {
        return ApiResponse.ok(positionSnapshotService.reconcile(snapshotId));
    }

    /** 查询快照详情。 */
    @GetMapping("/{id}")
    public ApiResponse<PositionSnapshotDetailVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(positionSnapshotService.getById(id));
    }

    /** 整批覆盖更新草稿及其明细。 */
    @PutMapping("/{id}")
    public ApiResponse<PositionSnapshotDetailVO> updateDraft(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePositionSnapshotDTO dto) {
        return ApiResponse.ok(positionSnapshotService.updateDraft(id, dto));
    }

    /** 确认草稿，确认后不可普通编辑。 */
    @PatchMapping("/{id}/confirm")
    public ApiResponse<PositionSnapshotDetailVO> confirm(@PathVariable Long id) {
        return ApiResponse.ok(positionSnapshotService.confirm(id));
    }

    /** 作废草稿或已确认快照。 */
    @PatchMapping("/{id}/cancel")
    public ApiResponse<PositionSnapshotDetailVO> cancel(@PathVariable Long id) {
        return ApiResponse.ok(positionSnapshotService.cancel(id));
    }
}
