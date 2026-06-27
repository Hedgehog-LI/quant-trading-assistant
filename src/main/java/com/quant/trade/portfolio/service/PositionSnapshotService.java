package com.quant.trade.portfolio.service;

import com.quant.trade.portfolio.convert.PositionSnapshotConverter;
import com.quant.trade.portfolio.dto.CreatePositionSnapshotDTO;
import com.quant.trade.portfolio.dto.UpdatePositionSnapshotDTO;
import com.quant.trade.portfolio.manager.PositionSnapshotManager;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import com.quant.trade.portfolio.vo.PositionSnapshotDetailVO;
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
 * 负责事务边界以及创建、整批更新、确认、作废和查询流程编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionSnapshotService {

    private final PositionSnapshotManager positionSnapshotManager;
    private final PositionSnapshotConverter positionSnapshotConverter;

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

    private String normalizeNullableCode(String code) {
        return StringUtils.isBlank(code) ? null : normalizeCode(code);
    }

    private String normalizeCode(String code) {
        return StringUtils.trimToEmpty(code).toUpperCase(Locale.ROOT);
    }
}
