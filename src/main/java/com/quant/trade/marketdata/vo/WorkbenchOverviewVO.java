package com.quant.trade.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 行情工作台概览 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchOverviewVO {
    private ProviderStatusVO providerStatus;
    private LocalDateTime latestSyncAt;
    private long totalSymbols;
    private long totalMinuteBars;
    private long totalDailyBars;
    private long unresolvedHighAlerts;
    private long unresolvedWarnAlerts;
    private long failedTasksToday;
    private List<MarketDataWatermarkVO> recentWatermarks;
    private List<MarketDataAlertVO> recentAlerts;
    private List<MarketTradingSessionVO> tradingSessions;
}
