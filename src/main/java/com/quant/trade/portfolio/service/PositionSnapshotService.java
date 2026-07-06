package com.quant.trade.portfolio.service;

import com.quant.trade.journal.flow.TradeFlowItem;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.portfolio.convert.PositionSnapshotConverter;
import com.quant.trade.portfolio.dto.CreatePositionSnapshotDTO;
import com.quant.trade.portfolio.dto.UpdatePositionSnapshotDTO;
import com.quant.trade.portfolio.manager.PositionSnapshotComparisonManager;
import com.quant.trade.portfolio.manager.PositionSnapshotManager;
import com.quant.trade.portfolio.manager.PositionSnapshotReconciliationManager;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import com.quant.trade.portfolio.vo.PositionSnapshotComparisonVO;
import com.quant.trade.portfolio.vo.PositionSnapshotDetailVO;
import com.quant.trade.portfolio.vo.PositionSnapshotReconciliationVO;
import com.quant.trade.portfolio.vo.PositionSnapshotSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 持仓快照应用服务。
 * <p>
 * 负责事务边界以及创建、整批更新、确认、作废、查询、对比和对账流程编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionSnapshotService {

    private final PositionSnapshotManager positionSnapshotManager;
    private final PositionSnapshotConverter positionSnapshotConverter;
    private final PositionSnapshotComparisonManager comparisonManager;
    private final PositionSnapshotReconciliationManager reconciliationManager;
    private final TradeJournalService tradeJournalService;

    /** 新建草稿或直接创建已确认快照。 */
    @Transactional
    public PositionSnapshotDetailVO create(CreatePositionSnapshotDTO dto) {
        PositionSnapshotDO snapshot = positionSnapshotConverter.toDO(dto);
        snapshot.setSourceType(normalizeCode(snapshot.getSourceType()));
        snapshot.setSnapshotStatus(normalizeCode(snapshot.getSnapshotStatus()));
        List<PositionSnapshotItemDO> items = positionSnapshotConverter.toItemDOList(dto.items());

        positionSnapshotManager.prepareForCreate(snapshot, items);
        positionSnapshotManager.insert(snapshot, items);

        log.info("Created position snapshot: id={}, status={}, positionCount={}",
                snapshot.getId(), snapshot.getSnapshotStatus(), snapshot.getPositionCount());
        return getById(snapshot.getId());
    }

    /**
     * 整批更新草稿。已确认或已作废快照不可修改。
     */
    @Transactional
    public PositionSnapshotDetailVO updateDraft(Long id, UpdatePositionSnapshotDTO dto) {
        PositionSnapshotDO snapshot = positionSnapshotManager.getByIdOrThrow(id);
        positionSnapshotConverter.updateDOFromDTO(dto, snapshot);
        List<PositionSnapshotItemDO> items = positionSnapshotConverter.toItemDOList(dto.items());

        positionSnapshotManager.prepareForUpdate(snapshot, items);
        positionSnapshotManager.replaceDraft(snapshot, items);

        log.info("Updated position snapshot draft: id={}, positionCount={}",
                id, snapshot.getPositionCount());
        return getById(id);
    }

    /** 确认草稿。 */
    @Transactional
    public PositionSnapshotDetailVO confirm(Long id) {
        positionSnapshotManager.confirm(id);
        log.info("Confirmed position snapshot: id={}", id);
        return getById(id);
    }

    /** 作废草稿或已确认快照。 */
    @Transactional
    public PositionSnapshotDetailVO cancel(Long id) {
        positionSnapshotManager.cancel(id);
        log.info("Canceled position snapshot: id={}", id);
        return getById(id);
    }

    /** 查询历史快照列表。 */
    public List<PositionSnapshotSummaryVO> list(LocalDate fromDate,
                                                LocalDate toDate,
                                                String status,
                                                String sourceType,
                                                boolean includeCanceled) {
        List<PositionSnapshotDO> records = positionSnapshotManager.list(
                fromDate,
                toDate,
                normalizeNullableCode(status),
                normalizeNullableCode(sourceType),
                includeCanceled);
        return positionSnapshotConverter.toSummaryVOList(records);
    }

    /** 查询快照详情。 */
    public PositionSnapshotDetailVO getById(Long id) {
        PositionSnapshotDO snapshot = positionSnapshotManager.getByIdOrThrow(id);
        List<PositionSnapshotItemDO> items = positionSnapshotManager.listItems(id);
        return positionSnapshotConverter.toDetailVO(snapshot, items);
    }

    /** 查询最新已确认快照；尚无已确认记录时返回 null。 */
    public PositionSnapshotDetailVO getLatestConfirmed() {
        PositionSnapshotDO snapshot = positionSnapshotManager.getLatestConfirmed();
        if (snapshot == null) {
            return null;
        }
        List<PositionSnapshotItemDO> items = positionSnapshotManager.listItems(snapshot.getId());
        return positionSnapshotConverter.toDetailVO(snapshot, items);
    }

    /**
     * 对比两个已确认快照的差异。
     * <p>
     * 仅支持 {@code CONFIRMED} 快照，基准快照时间必须严格早于目标快照时间，
     * 详细校验与计算口径由 {@link PositionSnapshotComparisonManager} 承载。
     */
    public PositionSnapshotComparisonVO compare(Long baseSnapshotId, Long targetSnapshotId) {
        PositionSnapshotDO base = positionSnapshotManager.getByIdOrThrow(baseSnapshotId);
        PositionSnapshotDO target = positionSnapshotManager.getByIdOrThrow(targetSnapshotId);
        List<PositionSnapshotItemDO> baseItems = positionSnapshotManager.listItems(baseSnapshotId);
        List<PositionSnapshotItemDO> targetItems = positionSnapshotManager.listItems(targetSnapshotId);
        return comparisonManager.compare(base, baseItems, target, targetItems);
    }

    /**
     * 已确认快照与截止时点 FIFO 账本对账。
     * <p>
     * 以数量为核心一致性判断；成本差异只展示不判错。对账只读，不会自动修改任何交易流水。
     */
    public PositionSnapshotReconciliationVO reconcile(Long snapshotId) {
        PositionSnapshotDO snapshot = positionSnapshotManager.getByIdOrThrow(snapshotId);
        List<PositionSnapshotItemDO> items = positionSnapshotManager.listItems(snapshotId);
        List<TradeFlowItem> flows = tradeJournalService.listFlowItemsUpTo(
                snapshot.getSnapshotDate(), snapshot.getSnapshotTime());
        return reconciliationManager.reconcile(snapshot, items, flows);
    }

    private String normalizeNullableCode(String code) {
        return StringUtils.isBlank(code) ? null : normalizeCode(code);
    }

    private String normalizeCode(String code) {
        return StringUtils.trimToEmpty(code).toUpperCase(Locale.ROOT);
    }
}
