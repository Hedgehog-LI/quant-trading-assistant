package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 采集计划 DO（market_data_sync_plan）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataSyncPlanDO {
    private Long id;
    private String planName;
    private String taskType;
    private String provider;
    private String scopeJson;
    private String intervalType;
    private String adjustType;
    private String triggerType;
    private String cronExpr;
    private Boolean includeAuction;
    private String collectFrequency;
    private Boolean enabled;
    private String description;
    private LocalDateTime lastRunAt;
    private Long lastTaskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
