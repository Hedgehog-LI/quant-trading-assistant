package com.quant.trade.marketdata.asset.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * series 查询参数（已校验并解析）。日 K 使用 date 字段，分钟 K 使用 time 字段，另一组为 null。
 */
public record MarketDataAssetSeriesQueryDTO(
        String interval,
        String dataSource,
        String adjustType,
        String fromRaw,
        String toRaw,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDateTime fromTime,
        LocalDateTime toTime) {

    /** 是否为日 K 查询。 */
    public boolean isDaily() {
        return "1D".equals(interval);
    }
}
