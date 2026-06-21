package com.quant.trade.portfolio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手工当前价快照数据库对象。
 * <p>
 * 对应数据库表 {@code portfolio_price_snapshot}，记录用户手工维护的某只股票某日的当前价。
 * 不是实时行情价，仅供估算浮动盈亏。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioPriceSnapshotDO {

    /** 主键 ID */
    private Long id;

    /** 股票代码 */
    private String symbol;

    /** 股票名称 */
    private String name;

    /** 手工当前价 */
    private BigDecimal currentPrice;

    /** 价格日期 */
    private LocalDate priceDate;

    /** 备注 */
    private String note;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
