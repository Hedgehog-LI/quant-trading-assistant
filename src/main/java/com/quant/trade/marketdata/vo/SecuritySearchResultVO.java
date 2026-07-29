package com.quant.trade.marketdata.vo;

import com.quant.trade.marketdata.enums.SecurityCatalogStatusEnum;

import java.time.Instant;
import java.util.List;

/** 搜索结果及 D1 本地目录状态元数据。 */
public record SecuritySearchResultVO(
        List<SecuritySearchItemVO> items,
        SecurityCatalogStatusEnum catalogStatus,
        Instant catalogUpdatedAt,
        boolean stale,
        boolean degraded
) {
}
