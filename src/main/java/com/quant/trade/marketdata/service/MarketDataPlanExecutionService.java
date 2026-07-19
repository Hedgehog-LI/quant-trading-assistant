package com.quant.trade.marketdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketDataSyncPlanMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskItemMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.dto.MinuteBarUpsertDTO;
import com.quant.trade.marketdata.manager.SyncPlanValidationManager;
import com.quant.trade.marketdata.manager.SyncPlanValidationManager.PlanScope;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import com.quant.trade.marketdata.model.MarketDataSyncTaskDO;
import com.quant.trade.marketdata.model.MarketDataSyncTaskItemDO;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderMinuteBar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 分钟 K 计划执行器。数据库状态使用短事务，provider 网络调用始终在事务外。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataPlanExecutionService {
    private static final int CLAIM_STALE_MINUTES = 60;

    private final MarketDataProvider provider;
    private final MarketDataSyncPlanMapper planMapper;
    private final MarketDataSyncTaskMapper taskMapper;
    private final MarketDataSyncTaskItemMapper itemMapper;
    private final SyncPlanValidationManager validationManager;
    private final TradingSessionManager tradingSessionManager;
    private final MinuteBarIngestService ingestService;
    private final TransactionTemplate txRequiresNew;
    private final ObjectMapper objectMapper;
    private final Clock marketDataClock;

    public MarketDataSyncTaskDO executeMinutePlan(MarketDataSyncPlanDO plan, LocalDateTime requestedAt) {
        var validation = validationManager.validate(plan);
        if (!plan.getProvider().equalsIgnoreCase(provider.getProviderCode())) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PLAN_INVALID,
                    "计划 provider=" + plan.getProvider() + "，当前运行 provider=" + provider.getProviderCode());
        }
        String token = UUID.randomUUID().toString();
        LocalDateTime now = requestedAt == null ? LocalDateTime.now(marketDataClock) : requestedAt;
        Integer claimed = txRequiresNew.execute(status -> planMapper.tryClaimRun(plan.getId(), token, now,
                now.minusMinutes(CLAIM_STALE_MINUTES)));
        if (claimed == null || claimed != 1) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PLAN_RUNNING,
                    "同一采集计划已有执行中的任务，请等待完成后重试");
        }

        MarketDataSyncTaskDO task = null;
        try {
            task = createParentTask(plan, token, now);
            executeSymbols(plan, validation.scope(), task, now);
            return taskMapper.selectById(task.getId());
        } catch (BusinessException exception) {
            if (task != null) finishUnexpectedTask(task.getId(), exception.getErrorCode(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            if (task != null) finishUnexpectedTask(task.getId(), ErrorCodeEnum.INTERNAL_ERROR, exception.getMessage());
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "分钟 K 计划执行异常: " + safe(exception.getMessage()));
        } finally {
            txRequiresNew.executeWithoutResult(status -> planMapper.releaseRunClaim(plan.getId(), token));
        }
    }

    private MarketDataSyncTaskDO createParentTask(MarketDataSyncPlanDO plan, String token, LocalDateTime now) {
        return txRequiresNew.execute(status -> {
            MarketDataSyncTaskDO task = MarketDataSyncTaskDO.builder()
                    .taskType(plan.getTaskType()).provider(plan.getProvider()).scopeJson(plan.getScopeJson())
                    .status(MarketDataConstants.TASK_STATUS_RUNNING)
                    .idempotencyKey(UUID.nameUUIDFromBytes(("plan#" + plan.getId() + "#" + token).getBytes()).toString())
                    .startedAt(now).build();
            taskMapper.insert(task);
            taskMapper.updateById(task);
            planMapper.setRunningTask(plan.getId(), token, task.getId());
            planMapper.updateLastRun(plan.getId(), task.getId(), now);
            return task;
        });
    }

    private void executeSymbols(MarketDataSyncPlanDO plan, PlanScope scope,
                                MarketDataSyncTaskDO task, LocalDateTime now) {
        LocalDate from = WorkbenchConstants.TASK_INTRADAY_MINUTE_REFRESH.equals(plan.getTaskType())
                ? now.toLocalDate() : scope.startDate();
        LocalDate to = WorkbenchConstants.TASK_INTRADAY_MINUTE_REFRESH.equals(plan.getTaskType())
                ? now.toLocalDate() : scope.endDate();
        int inserted = 0, updated = 0, skipped = 0, failed = 0;
        int succeededSymbols = 0, partialSymbols = 0, failedSymbols = 0;

        for (String symbol : scope.symbols()) {
            MarketDataSyncTaskItemDO item = createItem(plan, task.getId(), symbol, from, to, now);
            int itemInserted = 0, itemUpdated = 0, itemSkipped = 0, itemFailed = 0;
            String firstErrorCode = null, firstErrorMessage = null;
            try {
                List<ProviderMinuteBar> bars = provider.getMinuteBars(symbol, from, to,
                                plan.getIntervalType(), defaultAdjust(plan.getAdjustType())).stream()
                        .filter(bar -> !bar.barStartTime().toLocalDate().isBefore(from)
                                && !bar.barStartTime().toLocalDate().isAfter(to))
                        .filter(bar -> !bar.barStartTime().plusMinutes(intervalMinutes(plan.getIntervalType())).isAfter(now))
                        .toList();
                if (bars.isEmpty()) {
                    throw new BusinessException(ErrorCodeEnum.MARKET_DATA_EMPTY_RESULT,
                            "provider 未返回已闭合的分钟 K");
                }
                List<int[]> sessionWindows = tradingSessionManager.getSessionWindows(
                        WorkbenchConstants.MARKET_CN_A, Boolean.TRUE.equals(plan.getIncludeAuction()));
                for (ProviderMinuteBar bar : bars) {
                    if (!isInSession(bar.barStartTime(), sessionWindows)) {
                        itemSkipped++;
                        continue;
                    }
                    try {
                        MinuteBarIngestService.Result result = ingestService.upsert(toDTO(plan, bar));
                        switch (result.result()) {
                            case "INSERTED" -> itemInserted++;
                            case "UPDATED" -> itemUpdated++;
                            case "SKIPPED" -> itemSkipped++;
                            default -> {
                                itemFailed++;
                                if (firstErrorCode == null) {
                                    firstErrorCode = "MINUTE_BAR_" + result.result();
                                    firstErrorMessage = "分钟 K 写入结果: " + result.result();
                                }
                            }
                        }
                    } catch (BusinessException exception) {
                        itemFailed++;
                        if (firstErrorCode == null) {
                            firstErrorCode = exception.getErrorCode().getCode();
                            firstErrorMessage = exception.getMessage();
                        }
                    }
                }
            } catch (BusinessException exception) {
                itemFailed++;
                firstErrorCode = exception.getErrorCode().getCode();
                firstErrorMessage = exception.getMessage();
            } catch (Exception exception) {
                itemFailed++;
                firstErrorCode = ErrorCodeEnum.INTERNAL_ERROR.getCode();
                firstErrorMessage = exception.getMessage();
            }

            int itemSuccess = itemInserted + itemUpdated + itemSkipped;
            item.setRowCount(itemSuccess + itemFailed);
            item.setInsertedCount(itemInserted);
            item.setUpdatedCount(itemUpdated);
            item.setSkippedCount(itemSkipped);
            item.setErrorCode(firstErrorCode);
            item.setErrorMessage(safe(firstErrorMessage));
            if (itemFailed == 0) {
                item.setStatus(WorkbenchConstants.ITEM_SUCCEEDED);
                succeededSymbols++;
            } else if (itemSuccess > 0) {
                item.setStatus(WorkbenchConstants.ITEM_PARTIAL_FAILED);
                partialSymbols++;
            } else {
                item.setStatus(WorkbenchConstants.ITEM_FAILED);
                failedSymbols++;
            }
            item.setFinishedAt(LocalDateTime.now(marketDataClock));
            updateItem(item);
            inserted += itemInserted;
            updated += itemUpdated;
            skipped += itemSkipped;
            failed += itemFailed;
        }

        String status = failedSymbols == 0 && partialSymbols == 0
                ? MarketDataConstants.TASK_STATUS_SUCCEEDED
                : succeededSymbols > 0 || partialSymbols > 0
                ? MarketDataConstants.TASK_STATUS_PARTIAL_FAILED : MarketDataConstants.TASK_STATUS_FAILED;
        int success = inserted + updated + skipped;
        int total = success + failed;
        MarketDataSyncTaskDO done = new MarketDataSyncTaskDO();
        done.setId(task.getId());
        done.setStatus(status);
        done.setTotalCount(total);
        done.setSuccessCount(success);
        done.setFailCount(failed);
        done.setInsertedCount(inserted);
        done.setUpdatedCount(updated);
        done.setSkippedCount(skipped);
        done.setFinishedAt(LocalDateTime.now(marketDataClock));
        if (failed > 0) {
            done.setLastErrorCode(failedSymbols > 0 ? "MINUTE_PLAN_SYMBOL_FAILED" : "MINUTE_PLAN_PARTIAL_FAILED");
            done.setErrorSummaryJson(jsonSummary(succeededSymbols, partialSymbols, failedSymbols));
        }
        txRequiresNew.executeWithoutResult(tx -> taskMapper.updateById(done));
        log.info("分钟 K 计划执行结束: planId={}, taskId={}, status={}, inserted={}, skipped={}, failed={}",
                plan.getId(), task.getId(), status, inserted, skipped, failed);
    }

    private MarketDataSyncTaskItemDO createItem(MarketDataSyncPlanDO plan, Long taskId, String symbol,
                                                 LocalDate from, LocalDate to, LocalDateTime now) {
        return txRequiresNew.execute(status -> {
            MarketDataSyncTaskItemDO item = MarketDataSyncTaskItemDO.builder()
                    .taskId(taskId).planId(plan.getId()).canonicalSymbol(symbol)
                    .scopeDetail("{\"startDate\":\"" + from + "\",\"endDate\":\"" + to
                            + "\",\"intervalType\":\"" + plan.getIntervalType() + "\"}")
                    .status(WorkbenchConstants.ITEM_RUNNING).rowCount(0).insertedCount(0)
                    .updatedCount(0).skippedCount(0).startedAt(now).build();
            itemMapper.insert(item);
            return item;
        });
    }

    private void updateItem(MarketDataSyncTaskItemDO item) {
        txRequiresNew.executeWithoutResult(status -> itemMapper.updateById(item));
    }

    private MinuteBarUpsertDTO toDTO(MarketDataSyncPlanDO plan, ProviderMinuteBar bar) {
        MinuteBarUpsertDTO dto = new MinuteBarUpsertDTO();
        dto.setCanonicalSymbol(bar.canonicalSymbol());
        dto.setBarStartTime(bar.barStartTime());
        dto.setBarEndTime(bar.barStartTime().plusMinutes(intervalMinutes(plan.getIntervalType())));
        dto.setIntervalType(plan.getIntervalType());
        dto.setAdjustType(defaultAdjust(plan.getAdjustType()));
        dto.setDataSource(plan.getProvider());
        dto.setOpenPrice(bar.openPrice());
        dto.setHighPrice(bar.highPrice());
        dto.setLowPrice(bar.lowPrice());
        dto.setClosePrice(bar.closePrice());
        dto.setVolume(bar.volume() == null ? 0L : bar.volume());
        dto.setAmount(bar.amount() == null ? java.math.BigDecimal.ZERO : bar.amount());
        dto.setSessionType(WorkbenchConstants.SESSION_REGULAR);
        return dto;
    }

    private int intervalMinutes(String interval) {
        return switch (interval) {
            case "1M" -> 1;
            case "5M" -> 5;
            case "15M" -> 15;
            case "30M" -> 30;
            case "60M" -> 60;
            default -> throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE, "分钟粒度不合法: " + interval);
        };
    }

    private boolean isInSession(LocalDateTime barStartTime, List<int[]> sessionWindows) {
        int hhmm = barStartTime.getHour() * 100 + barStartTime.getMinute();
        for (int[] window : sessionWindows) {
            if (window.length >= 2 && hhmm >= window[0] && hhmm < window[1]) return true;
        }
        return false;
    }

    private String defaultAdjust(String adjust) { return adjust == null || adjust.isBlank() ? "NONE" : adjust; }

    private void finishUnexpectedTask(Long taskId, ErrorCodeEnum code, String message) {
        txRequiresNew.executeWithoutResult(status -> {
            MarketDataSyncTaskDO failed = new MarketDataSyncTaskDO();
            failed.setId(taskId);
            failed.setStatus(MarketDataConstants.TASK_STATUS_FAILED);
            failed.setFinishedAt(LocalDateTime.now(marketDataClock));
            failed.setLastErrorCode(code.getCode());
            failed.setErrorSummaryJson("{\"message\":\"" + safeJson(message) + "\"}");
            taskMapper.updateById(failed);
            itemMapper.markFailedIfNonTerminal(taskId, code.getCode(), safe(message), LocalDateTime.now(marketDataClock));
        });
    }

    private String jsonSummary(int succeeded, int partial, int failed) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "succeededSymbols", succeeded, "partialSymbols", partial, "failedSymbols", failed));
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
    private String safeJson(String value) {
        String safe = safe(value);
        return safe == null ? "" : safe.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
