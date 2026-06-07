package com.quant.trade.journal.service;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.journal.convert.TradeJournalConverter;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.dto.UpdateReviewStatusDTO;
import com.quant.trade.journal.dto.UpdateTradeJournalDTO;
import com.quant.trade.journal.manager.TradeJournalManager;
import com.quant.trade.journal.model.TradeJournalDO;
import com.quant.trade.journal.vo.TradeJournalVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 交易记录应用服务。
 * <p>
 * 负责事务边界和业务流程编排，核心校验和 DB 读写委托给 {@link TradeJournalManager}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeJournalService {

    private final TradeJournalManager tradeJournalManager;
    private final TradeJournalConverter tradeJournalConverter;

    /**
     * 新增交易记录。
     *
     * @return 包含风险提示的交易记录 VO
     */
    @Transactional
    public TradeJournalVO create(CreateTradeJournalDTO dto) {
        TradeJournalDO record = tradeJournalConverter.toDO(dto);
        // 默认值
        if (record.getReviewStatus() == null) {
            record.setReviewStatus(ReviewStatusEnum.PENDING.getCode());
        }

        // 校验并自动计算
        tradeJournalManager.validateAndFillForCreate(record);

        // 买入无止损 -> warning（不阻断）
        List<String> warnings = Collections.emptyList();
        if (TradeSideEnum.BUY.getCode().equals(record.getSide()) && record.getPlanStopLoss() == null) {
            warnings = List.of(MessageConstants.JOURNAL_BUY_NO_STOP_LOSS);
        }

        tradeJournalManager.insert(record);

        log.info("Created trade journal: symbol={}, side={}, date={}",
                record.getSymbol(), record.getSide(), record.getTradeDate());

        TradeJournalVO vo = tradeJournalConverter.toVO(record);
        return new TradeJournalVO(
                vo.id(), vo.tradeDate(), vo.tradeTime(), vo.symbol(), vo.name(),
                vo.side(), vo.price(), vo.quantity(), vo.amount(), vo.positionRatio(),
                vo.planId(), vo.reason(), vo.planStopLoss(), vo.planTakeProfit(),
                vo.followedPlan(), vo.emotionTags(), vo.mistakeTags(), vo.actualResult(),
                vo.reviewStatus(), vo.createdAt(), vo.updatedAt(), warnings);
    }

    public List<TradeJournalVO> list(LocalDate date, String symbol, String reviewStatus) {
        List<TradeJournalDO> records = tradeJournalManager.listByFilter(date, symbol, reviewStatus);
        return tradeJournalConverter.toVOList(records);
    }

    public TradeJournalVO getById(Long id) {
        TradeJournalDO record = tradeJournalManager.getByIdOrThrow(id);
        return tradeJournalConverter.toVO(record);
    }

    @Transactional
    public TradeJournalVO update(Long id, UpdateTradeJournalDTO dto) {
        TradeJournalDO existing = tradeJournalManager.getByIdOrThrow(id);
        tradeJournalConverter.updateDOFromDTO(dto, existing);
        existing.setId(id);

        tradeJournalManager.validateForUpdate(existing);
        tradeJournalManager.updateById(existing);

        log.info("Updated trade journal: id={}", id);
        return tradeJournalConverter.toVO(tradeJournalManager.selectById(id));
    }

    @Transactional
    public TradeJournalVO updateReviewStatus(Long id, UpdateReviewStatusDTO dto) {
        tradeJournalManager.validateReviewStatus(dto.reviewStatus());
        TradeJournalDO existing = tradeJournalManager.getByIdOrThrow(id);

        existing.setReviewStatus(dto.reviewStatus());
        tradeJournalManager.updateById(existing);

        log.info("Updated journal review status: id={}, status={}", id, dto.reviewStatus());
        return tradeJournalConverter.toVO(tradeJournalManager.selectById(id));
    }

    /**
     * 批量标记为已复盘（供 Review 模块调用）。
     */
    @Transactional
    public void markAsReviewed(List<Long> journalIds) {
        tradeJournalManager.markAsReviewed(journalIds);
    }

    public long countByDate(LocalDate date) {
        return tradeJournalManager.countByDate(date);
    }

    public long countByReviewStatus(String reviewStatus) {
        return tradeJournalManager.countByReviewStatus(reviewStatus);
    }
}
