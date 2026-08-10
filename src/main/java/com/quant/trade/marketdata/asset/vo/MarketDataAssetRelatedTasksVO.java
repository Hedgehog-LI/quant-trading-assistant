package com.quant.trade.marketdata.asset.vo;

import java.util.List;

/**
 * related-tasks 响应：与该证券相关的采集计划 + 最近采集记录。
 * <p>
 * 血缘口径：按 scope_json 包含证券、interval 匹配、时间范围过滤的“相关计划/记录”，
 * 不声称与具体 K 线的精确逐条血缘。
 */
public record MarketDataAssetRelatedTasksVO(
        MarketDataAssetSecurityVO security,
        List<RelatedTaskItem> plans,
        List<RelatedTaskItem> runs) {

    /** 计划或采集记录的一行。kind = "PLAN" / "RUN"。 */
    public record RelatedTaskItem(
            String kind,
            Long id,
            String name,
            String taskType,
            String intervalType,
            String status,
            String startDate,
            String endDate,
            String startedAt,
            String finishedAt,
            String errorCode,
            String errorMessage) {
    }
}
