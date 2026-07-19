package com.quant.trade.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 采集计划 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataSyncPlanVO {
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
    /** VALID / NEEDS_ATTENTION，用于历史非法计划的页面治理。 */
    private String configurationStatus;
    private List<String> validationErrors;
    private Boolean manuallyRunnable;
    private Boolean automaticallyRunnable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
