package com.quant.trade.watchlist.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 自选股响应 VO。
 */
public record WatchlistVO(

        /** 主键 ID */
        Long id,

        /** 股票代码 */
        String symbol,

        /** 股票名称 */
        String name,

        /** 市场类型 */
        String market,

        /** 分组名称 */
        String groupName,

        /** 关注理由 */
        String watchReason,

        /** 交易风格 */
        String tradeStyle,

        /** 关注等级 */
        String attentionLevel,

        /** 支撑位价格 */
        BigDecimal supportPrice,

        /** 压力位价格 */
        BigDecimal resistancePrice,

        /** 默认止损价 */
        BigDecimal stopLossPrice,

        /** 风险备注 */
        String riskNote,

        /** 是否启用 */
        Boolean enabled,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {}
