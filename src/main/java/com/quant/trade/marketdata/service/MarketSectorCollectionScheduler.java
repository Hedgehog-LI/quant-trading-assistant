package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketSectorCollectionConstants;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketSectorRankingConfigMapper;
import com.quant.trade.marketdata.dao.MarketSectorWatchMapper;
import com.quant.trade.marketdata.manager.MarketSectorScheduleManager;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** 周期扫描全市场板块排行与关注板块明细，不在非交易时段发起盘中请求。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "qta.market-data.scheduler", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class MarketSectorCollectionScheduler {
    private final MarketSectorRankingConfigMapper configMapper;
    private final MarketSectorWatchMapper watchMapper;
    private final MarketSectorRankingService rankingService;
    private final MarketSectorWatchService watchService;
    private final MarketSectorScheduleManager scheduleManager;
    private final TradingSessionManager tradingSessionManager;
    private final Clock marketDataClock;

    @Scheduled(fixedDelayString = "${qta.market-data.sector-scheduler.scan-delay-ms:30000}",
            initialDelayString = "${qta.market-data.sector-scheduler.initial-delay-ms:10000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        try {
            configMapper.selectRunnable(now).forEach(config -> {
                if (isTradingDay(config.getMarketCode())) {
                    scheduleManager.nextWindow(config, marketDataClock.instant(), marketDataClock.getZone())
                            .ifPresent(window -> runRanking(config, window));
                }
            });
        } catch (RuntimeException exception) {
            log.error("全市场板块排行扫描异常，后续周期仍会继续: {}", exception.getMessage(), exception);
        }
        try {
            watchMapper.selectAutoRunnable(now).forEach(watch -> {
                if (!isTradingDay(watch.getMarketCode())) {
                    return;
                }
                int interval = watch.getCollectIntervalMinutes() == null
                        ? MarketSectorCollectionConstants.DEFAULT_WATCH_INTERVAL_MINUTES
                        : watch.getCollectIntervalMinutes();
                scheduleManager.intradayBucket(watch.getMarketCode(), interval, marketDataClock.instant(),
                                marketDataClock.getZone())
                        .filter(bucket -> watch.getLastAutoCollectedAt() == null
                                || watch.getLastAutoCollectedAt().isBefore(bucket))
                        .ifPresent(bucket -> runWatch(watch, bucket));
            });
        } catch (RuntimeException exception) {
            log.error("关注板块扫描异常，后续周期仍会继续: {}", exception.getMessage(), exception);
        }
    }

    private boolean isTradingDay(String marketCode) {
        String calendarMarket = MarketSectorCollectionConstants.MARKET_CN.equals(marketCode)
                ? WorkbenchConstants.MARKET_CN_A : marketCode;
        return tradingSessionManager.isTradingDay(calendarMarket,
                scheduleManager.tradeDate(marketCode, marketDataClock.instant()));
    }

    private void runRanking(com.quant.trade.marketdata.model.MarketSectorRankingConfigDO config,
                            MarketSectorScheduleManager.CollectionWindow window) {
        try {
            rankingService.collectScheduled(config, window);
        } catch (BusinessException exception) {
            logBusiness("板块排行", config.getId(), exception);
        } catch (RuntimeException exception) {
            log.error("板块排行采集异常: configId={}, message={}", config.getId(), exception.getMessage(), exception);
        }
    }

    private void runWatch(com.quant.trade.marketdata.model.MarketSectorWatchDO watch, LocalDateTime bucket) {
        try {
            watchService.collectAuto(watch, bucket);
        } catch (BusinessException exception) {
            logBusiness("关注板块", watch.getId(), exception);
        } catch (RuntimeException exception) {
            log.error("关注板块采集异常: watchId={}, message={}", watch.getId(), exception.getMessage(), exception);
        }
    }

    private void logBusiness(String type, Long id, BusinessException exception) {
        if (exception.getErrorCode() == ErrorCodeEnum.MARKET_DATA_PLAN_RUNNING) {
            log.info("{}正在执行，跳过本周期: id={}", type, id);
        } else {
            log.warn("{}采集失败: id={}, code={}, message={}", type, id,
                    exception.getErrorCode().getCode(), exception.getMessage());
        }
    }
}
