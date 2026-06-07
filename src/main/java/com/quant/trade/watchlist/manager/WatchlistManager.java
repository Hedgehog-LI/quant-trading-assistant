package com.quant.trade.watchlist.manager;

import com.quant.trade.common.enums.AttentionLevelEnum;
import com.quant.trade.common.enums.MarketTypeEnum;
import com.quant.trade.common.enums.TradeStyleEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.watchlist.dao.WatchlistMapper;
import com.quant.trade.watchlist.model.WatchlistDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 自选股领域业务规则层。
 * <p>
 * 负责核心业务校验、数据查询和聚合逻辑，
 * 被 {@link com.quant.trade.watchlist.service.WatchlistService} 调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistManager {

    private final WatchlistMapper watchlistMapper;

    /**
     * 校验并标准化创建参数。
     * <p>
     * 包括：symbol trim+大写、枚举合法性、价格合理性。
     *
     * @param record 待创建的自选股记录（会被原地修改）
     */
    public void validateAndNormalizeForCreate(WatchlistDO record) {
        // symbol trim 并转大写
        record.setSymbol(normalizeSymbol(record.getSymbol()));

        // name 必填
        if (StringUtils.isBlank(record.getName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "name is required");
        }

        // 唯一性校验
        if (watchlistMapper.existsBySymbol(record.getSymbol()) > 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_RESOURCE,
                    "Symbol already exists in watchlist: " + record.getSymbol());
        }

        // 枚举合法性校验
        validateEnumFields(record);

        // 价格合理性校验
        validatePriceLevels(record.getSupportPrice(), record.getResistancePrice());
    }

    /**
     * 校验并标准化更新参数。
     *
     * @param record 更新数据
     * @param existing 已有记录
     */
    public void validateForUpdate(WatchlistDO record, WatchlistDO existing) {
        validateEnumFields(record);
        validatePriceLevels(record.getSupportPrice(), record.getResistancePrice());
    }

    /**
     * 根据 ID 查询，不存在则抛异常。
     */
    public WatchlistDO getByIdOrThrow(Long id) {
        WatchlistDO record = watchlistMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    "Watchlist entry not found: " + id);
        }
        return record;
    }

    /**
     * 按条件筛选。
     */
    public List<WatchlistDO> listByFilter(Boolean enabled, String keyword, String tradeStyle) {
        return watchlistMapper.selectByFilter(enabled, keyword, tradeStyle);
    }

    /**
     * 查询启用且高关注的自选股。
     */
    public List<WatchlistDO> listHighAttention() {
        return watchlistMapper.selectEnabledHighAttention(AttentionLevelEnum.HIGH.getCode());
    }

    /**
     * 统计启用数量。
     */
    public long countEnabled() {
        return watchlistMapper.countEnabled();
    }

    // ==================== DB 读写 ====================

    /**
     * 插入一条自选股记录。
     */
    public void insert(WatchlistDO record) {
        watchlistMapper.insert(record);
    }

    /**
     * 根据主键更新。
     */
    public void updateById(WatchlistDO record) {
        watchlistMapper.updateById(record);
    }

    /**
     * 根据主键查询（允许返回 null）。
     */
    public WatchlistDO selectById(Long id) {
        return watchlistMapper.selectById(id);
    }

    // ==================== 私有方法 ====================

    private String normalizeSymbol(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "symbol is required");
        }
        return symbol.trim().toUpperCase();
    }

    private void validateEnumFields(WatchlistDO record) {
        if (StringUtils.isNotBlank(record.getMarket()) && !MarketTypeEnum.isValid(record.getMarket())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "Invalid market: " + record.getMarket());
        }
        if (StringUtils.isNotBlank(record.getTradeStyle()) && !TradeStyleEnum.isValid(record.getTradeStyle())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "Invalid tradeStyle: " + record.getTradeStyle());
        }
        if (StringUtils.isNotBlank(record.getAttentionLevel())
                && !AttentionLevelEnum.isValid(record.getAttentionLevel())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "Invalid attentionLevel: " + record.getAttentionLevel());
        }
    }

    private void validatePriceLevels(BigDecimal supportPrice, BigDecimal resistancePrice) {
        if (supportPrice != null && resistancePrice != null
                && resistancePrice.compareTo(supportPrice) <= 0) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "resistancePrice must be greater than supportPrice");
        }
    }
}
