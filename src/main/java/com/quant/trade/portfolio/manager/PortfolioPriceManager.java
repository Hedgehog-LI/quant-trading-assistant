package com.quant.trade.portfolio.manager;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.portfolio.dao.PortfolioPriceMapper;
import com.quant.trade.portfolio.model.PortfolioPriceSnapshotDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手工当前价快照领域业务规则层。
 * <p>
 * 负责快照的校验、upsert（相同 symbol + priceDate 覆盖价格）、最新价查询，
 * 被 {@link com.quant.trade.portfolio.service.PortfolioService} 调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioPriceManager {

    private final PortfolioPriceMapper portfolioPriceMapper;

    /**
     * 新增或更新手工当前价（相同 symbol + priceDate 视为同一条，覆盖价格）。
     *
     * @param record 快照数据（symbol 会被 trim+大写）
     * @return 持久化后的快照
     */
    public PortfolioPriceSnapshotDO upsert(PortfolioPriceSnapshotDO record) {
        record.setSymbol(normalizeSymbol(record.getSymbol()));
        validateCurrentPrice(record.getCurrentPrice());

        PortfolioPriceSnapshotDO existing = portfolioPriceMapper
                .selectBySymbolAndDate(record.getSymbol(), record.getPriceDate());
        if (existing != null) {
            // 仅覆盖非 null 字段，保持返回对象与 DB 一致（与动态 update 语义对齐）
            if (record.getName() != null) {
                existing.setName(record.getName());
            }
            existing.setCurrentPrice(record.getCurrentPrice());
            if (record.getNote() != null) {
                existing.setNote(record.getNote());
            }
            portfolioPriceMapper.updateById(existing);
            log.info("Updated price snapshot: symbol={}, date={}, price={}",
                    existing.getSymbol(), existing.getPriceDate(), existing.getCurrentPrice());
            return existing;
        }

        portfolioPriceMapper.insert(record);
        log.info("Inserted price snapshot: symbol={}, date={}, price={}",
                record.getSymbol(), record.getPriceDate(), record.getCurrentPrice());
        return record;
    }

    /**
     * 查询全部快照（按价格日期倒序）。
     */
    public List<PortfolioPriceSnapshotDO> list() {
        return portfolioPriceMapper.selectAll();
    }

    /**
     * 取所有股票的最新手工价（symbol -> price）。
     */
    public Map<String, BigDecimal> getLatestPriceMap() {
        List<PortfolioPriceSnapshotDO> latest = portfolioPriceMapper.selectLatestBySymbols();
        Map<String, BigDecimal> map = new HashMap<>(latest.size());
        for (PortfolioPriceSnapshotDO snapshot : latest) {
            map.put(snapshot.getSymbol(), snapshot.getCurrentPrice());
        }
        return map;
    }

    /**
     * 取单只股票的最新手工价，无则返回 null。
     */
    public BigDecimal getLatestPrice(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            return null;
        }
        PortfolioPriceSnapshotDO latest = portfolioPriceMapper
                .selectLatestBySymbol(symbol.trim().toUpperCase());
        return latest != null ? latest.getCurrentPrice() : null;
    }

    // ==================== 私有方法 ====================

    private String normalizeSymbol(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "symbol is required");
        }
        return symbol.trim().toUpperCase();
    }

    private void validateCurrentPrice(BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "currentPrice must be greater than 0");
        }
    }
}
