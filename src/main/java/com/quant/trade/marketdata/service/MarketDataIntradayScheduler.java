package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketDataSyncPlanMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskItemMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.manager.SyncPlanValidationManager;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 单实例轻量盘中 scheduler：只在 A 股交易日与配置窗口内触发分钟刷新计划。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "qta.market-data.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataIntradayScheduler {
    private final MarketDataSyncPlanMapper planMapper;
    private final MarketDataSyncTaskMapper taskMapper;
    private final MarketDataSyncTaskItemMapper itemMapper;
    private final SyncPlanValidationManager validationManager;
    private final TradingSessionManager tradingSessionManager;
    private final MarketDataPlanExecutionService executionService;
    private final TransactionTemplate txRequiresNew;
    private final Clock marketDataClock;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAbandonedRuns() {
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        for (MarketDataSyncPlanDO plan : planMapper.selectClaimedPlans()) {
            txRequiresNew.executeWithoutResult(status -> {
                if (plan.getRunningTaskId() != null) {
                    taskMapper.markFailedIfNonTerminal(plan.getRunningTaskId(),
                            ErrorCodeEnum.MARKET_DATA_RUN_RECOVERED.getCode(),
                            "{\"message\":\"服务重启后收敛遗留任务\"}", now);
                    itemMapper.markFailedIfNonTerminal(plan.getRunningTaskId(),
                            ErrorCodeEnum.MARKET_DATA_RUN_RECOVERED.getCode(),
                            "服务重启后收敛遗留任务", now);
                }
                planMapper.releaseRunClaim(plan.getId(), plan.getRunClaimToken());
            });
            log.warn("已收敛服务重启前遗留的行情采集计划: planId={}, taskId={}",
                    plan.getId(), plan.getRunningTaskId());
        }
    }

    @Scheduled(fixedDelayString = "${qta.market-data.scheduler.scan-delay-ms:30000}",
            initialDelayString = "${qta.market-data.scheduler.initial-delay-ms:5000}")
    public void scan() {
        scanAt(LocalDateTime.now(marketDataClock));
    }

    /** 可由测试使用固定时间直接调用，无需真实等待交易时段。 */
    public void scanAt(LocalDateTime now) {
        try {
            List<MarketDataSyncPlanDO> plans = planMapper.selectAutoTriggerPlans(
                    WorkbenchConstants.TASK_INTRADAY_MINUTE_REFRESH,
                    WorkbenchConstants.TRIGGER_INTRADAY, true);
            for (MarketDataSyncPlanDO plan : plans) {
                tryRun(plan, now);
            }
        } catch (Exception exception) {
            log.error("盘中行情 scheduler 扫描异常，后续周期仍会继续: {}", exception.getMessage(), exception);
        }
    }

    private void tryRun(MarketDataSyncPlanDO plan, LocalDateTime now) {
        try {
            var validation = validationManager.inspect(plan);
            if (!validation.automaticallyRunnable()) {
                log.warn("跳过配置不完整的盘中计划: planId={}, errors={}", plan.getId(), validation.errors());
                return;
            }
            if (!tradingSessionManager.isTradingDay(WorkbenchConstants.MARKET_CN_A, now.toLocalDate())) return;
            if (!inAllowedSession(plan, now)) return;
            Integer frequency = validationManager.frequencySeconds(plan.getCollectFrequency());
            if (frequency == null) return;
            if (plan.getLastRunAt() != null && now.isBefore(plan.getLastRunAt().plusSeconds(frequency))) return;
            executionService.executeMinutePlan(plan, now);
        } catch (com.quant.trade.common.exception.BusinessException exception) {
            if (exception.getErrorCode() == ErrorCodeEnum.MARKET_DATA_PLAN_RUNNING) {
                log.info("盘中计划仍在执行，跳过本周期: planId={}", plan.getId());
            } else {
                log.warn("盘中计划本周期执行失败，后续周期仍会继续: planId={}, code={}, message={}",
                        plan.getId(), exception.getErrorCode().getCode(), exception.getMessage());
            }
        } catch (Exception exception) {
            log.error("盘中计划异常，后续周期仍会继续: planId={}, message={}",
                    plan.getId(), exception.getMessage(), exception);
        }
    }

    private boolean inAllowedSession(MarketDataSyncPlanDO plan, LocalDateTime now) {
        int hhmm = now.getHour() * 100 + now.getMinute();
        List<int[]> windows = tradingSessionManager.getSessionWindows(WorkbenchConstants.MARKET_CN_A,
                Boolean.TRUE.equals(plan.getIncludeAuction()));
        for (int[] window : windows) {
            if (hhmm >= window[0] && hhmm < window[1]) {
                // 开盘第一段在首根 bar 闭合前不请求，避免制造空任务。
                if (window[0] == 930) {
                    int elapsed = (now.getHour() * 60 + now.getMinute()) - (9 * 60 + 30);
                    return elapsed >= intervalMinutes(plan.getIntervalType());
                }
                return true;
            }
        }
        return false;
    }

    private int intervalMinutes(String interval) {
        return switch (interval) {
            case "1M" -> 1;
            case "5M" -> 5;
            case "15M" -> 15;
            case "30M" -> 30;
            case "60M" -> 60;
            default -> Integer.MAX_VALUE;
        };
    }
}
