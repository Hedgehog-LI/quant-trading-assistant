package com.quant.trade.marketdata.service;

import com.quant.trade.marketdata.config.SecurityDirectoryProperties;
import com.quant.trade.marketdata.constant.SecurityDirectoryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 证券目录同步调度（D3）。默认安全关闭：{@code scheduler.enabled=false}（不带 matchIfMissing），
 * 关闭时该 bean 不装配。启用后按 cron 每日增量（INCREMENTAL）与每周全量对账（FULL）。
 * <p>
 * 提供可直接调用的测试 seam {@link #triggerDailySync(LocalDateTime)} /
 * {@link #triggerWeeklyReconciliation(LocalDateTime)}，镜像 MarketDataIntradayScheduler.scanAt。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "qta.market-data.security-directory.scheduler", name = "enabled",
        havingValue = "true")
public class SecurityDirectorySyncScheduler {

    private final SecurityDirectorySyncService syncService;
    private final SecurityDirectoryProperties properties;
    private final Clock marketDataClock;

    /** 每日增量同步（Asia/Shanghai cron，默认 06:30:02）。 */
    @Scheduled(cron = "${qta.market-data.security-directory.scheduler.daily-cron:"
            + SecurityDirectoryConstants.DEFAULT_DAILY_CRON + "}", zone = "Asia/Shanghai")
    public void dailySync() {
        triggerDailySync(LocalDateTime.now(marketDataClock));
    }

    /** 每周全量对账（Asia/Shanghai cron，默认 周一 04:30）。 */
    @Scheduled(cron = "${qta.market-data.security-directory.scheduler.weekly-cron:"
            + SecurityDirectoryConstants.DEFAULT_WEEKLY_CRON + "}", zone = "Asia/Shanghai")
    public void weeklyReconciliation() {
        triggerWeeklyReconciliation(LocalDateTime.now(marketDataClock));
    }

    /** 测试 seam：直接触发每日增量同步（生产环境由 @Scheduled cron 驱动）。 */
    public void triggerDailySync(LocalDateTime now) {
        try {
            syncService.triggerScheduled(SecurityDirectoryConstants.MODE_INCREMENTAL);
        } catch (RuntimeException exception) {
            log.error("证券目录每日增量同步异常，后续周期仍会继续: {}", exception.getMessage(), exception);
        }
    }

    /** 测试 seam：直接触发每周全量对账（生产环境由 @Scheduled cron 驱动）。 */
    public void triggerWeeklyReconciliation(LocalDateTime now) {
        try {
            syncService.triggerScheduled(SecurityDirectoryConstants.MODE_FULL);
        } catch (RuntimeException exception) {
            log.error("证券目录每周全量对账异常，后续周期仍会继续: {}", exception.getMessage(), exception);
        }
    }
}
