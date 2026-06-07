package com.quant.trade.watchlist.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建自选股请求 DTO。
 */
public record CreateWatchlistDTO(

        /** 股票代码（必填，自动 trim 并转大写） */
        @NotBlank(message = "symbol is required")
        @Size(max = 32, message = "symbol must be at most 32 characters")
        String symbol,

        /** 股票名称（必填） */
        @NotBlank(message = "name is required")
        @Size(max = 128, message = "name must be at most 128 characters")
        String name,

        /** 市场类型 */
        @Size(max = 32, message = "market must be at most 32 characters")
        String market,

        /** 分组名称 */
        @Size(max = 64, message = "groupName must be at most 64 characters")
        String groupName,

        /** 关注理由 */
        @Size(max = 1024, message = "watchReason must be at most 1024 characters")
        String watchReason,

        /** 交易风格 */
        @Size(max = 32, message = "tradeStyle must be at most 32 characters")
        String tradeStyle,

        /** 关注等级 */
        @Size(max = 32, message = "attentionLevel must be at most 32 characters")
        String attentionLevel,

        /** 支撑位价格（大于 0） */
        @DecimalMin(value = "0", inclusive = false, message = "supportPrice must be greater than 0")
        BigDecimal supportPrice,

        /** 压力位价格（大于 0） */
        @DecimalMin(value = "0", inclusive = false, message = "resistancePrice must be greater than 0")
        BigDecimal resistancePrice,

        /** 默认止损价（大于 0） */
        @DecimalMin(value = "0", inclusive = false, message = "stopLossPrice must be greater than 0")
        BigDecimal stopLossPrice,

        /** 风险备注 */
        @Size(max = 1024, message = "riskNote must be at most 1024 characters")
        String riskNote
) {}
