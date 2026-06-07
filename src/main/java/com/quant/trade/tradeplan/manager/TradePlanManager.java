package com.quant.trade.tradeplan.manager;

import com.quant.trade.common.enums.PlanStatusEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.tradeplan.dao.TradePlanMapper;
import com.quant.trade.tradeplan.model.TradePlanDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 交易计划领域业务规则层。
 * <p>
 * 负责盘前计划的核心校验：唯一性、允许交易前置条件、止盈止损合理性等。
 * <p>
 * 注意：计划只是用户的盘前纪律记录，不是交易建议。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradePlanManager {

    private final TradePlanMapper tradePlanMapper;

    /**
     * 校验创建参数。
     */
    public void validateForCreate(TradePlanDO record) {
        // 枚举校验
        validatePlanStatus(record.getPlanStatus());

        // 唯一性：同一 symbol + planDate 只能有一条
        if (tradePlanMapper.existsBySymbolAndDate(record.getSymbol(), record.getPlanDate()) > 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_RESOURCE,
                    "Trade plan already exists for symbol=" + record.getSymbol()
                            + " date=" + record.getPlanDate());
        }

        // 允许交易前置条件
        if (Boolean.TRUE.equals(record.getAllowedToTrade())) {
            validateAllowedToTrade(record);
        }

        // 止盈止损合理性
        validateTakeProfit(record.getStopLossPrice(), record.getTakeProfitPrice());
    }

    /**
     * 校验更新参数。
     *
     * @param record   更新数据
     * @param existing 已有记录
     */
    public void validateForUpdate(TradePlanDO record, TradePlanDO existing) {
        validatePlanStatus(record.getPlanStatus());

        boolean allowedToTrade = record.getAllowedToTrade() != null
                ? record.getAllowedToTrade() : existing.getAllowedToTrade();

        if (allowedToTrade) {
            TradePlanDO merged = mergeForValidation(record, existing);
            validateAllowedToTrade(merged);
            validateTakeProfit(merged.getStopLossPrice(), merged.getTakeProfitPrice());
        }
    }

    /**
     * 根据 ID 查询，不存在则抛异常。
     */
    public TradePlanDO getByIdOrThrow(Long id) {
        TradePlanDO record = tradePlanMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    "Trade plan not found: " + id);
        }
        return record;
    }

    /**
     * 按条件筛选。
     */
    public List<TradePlanDO> listByFilter(LocalDate date, String symbol) {
        return tradePlanMapper.selectByFilter(date, symbol);
    }

    public long countActiveByDate(LocalDate date) {
        return tradePlanMapper.countActiveByDate(date);
    }

    // ==================== DB 读写 ====================

    public void insert(TradePlanDO record) {
        tradePlanMapper.insert(record);
    }

    public void updateById(TradePlanDO record) {
        tradePlanMapper.updateById(record);
    }

    public TradePlanDO selectById(Long id) {
        return tradePlanMapper.selectById(id);
    }

    // ==================== 私有方法 ====================

    private void validatePlanStatus(String planStatus) {
        if (!PlanStatusEnum.isValid(planStatus)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "Invalid planStatus: " + planStatus);
        }
    }

    /**
     * allowedToTrade=true 时必须有 buyCondition、stopLossPrice、plannedPositionRatio。
     */
    private void validateAllowedToTrade(TradePlanDO record) {
        if (record.getBuyCondition() == null || record.getBuyCondition().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "allowedToTrade=true requires buyCondition");
        }
        if (record.getStopLossPrice() == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "allowedToTrade=true requires stopLossPrice");
        }
        if (record.getPlannedPositionRatio() == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "allowedToTrade=true requires plannedPositionRatio");
        }
    }

    private void validateTakeProfit(BigDecimal stopLossPrice, BigDecimal takeProfitPrice) {
        if (takeProfitPrice != null && stopLossPrice != null
                && takeProfitPrice.compareTo(stopLossPrice) <= 0) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "takeProfitPrice must be greater than stopLossPrice");
        }
    }

    private TradePlanDO mergeForValidation(TradePlanDO update, TradePlanDO existing) {
        return TradePlanDO.builder()
                .buyCondition(update.getBuyCondition() != null ? update.getBuyCondition() : existing.getBuyCondition())
                .stopLossPrice(update.getStopLossPrice() != null ? update.getStopLossPrice() : existing.getStopLossPrice())
                .takeProfitPrice(update.getTakeProfitPrice() != null ? update.getTakeProfitPrice() : existing.getTakeProfitPrice())
                .plannedPositionRatio(update.getPlannedPositionRatio() != null
                        ? update.getPlannedPositionRatio() : existing.getPlannedPositionRatio())
                .build();
    }
}
