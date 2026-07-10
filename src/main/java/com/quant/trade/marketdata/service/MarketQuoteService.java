package com.quant.trade.marketdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dao.*;
import com.quant.trade.marketdata.dto.CreateSyncTaskDTO;
import com.quant.trade.marketdata.dto.FetchQuotesRequestDTO;
import com.quant.trade.marketdata.model.*;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderDailyBar;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderQuote;
import com.quant.trade.marketdata.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 行情服务：provider 状态、最新价快照、同步任务执行、异常提醒。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketQuoteService {

    private final MarketDataProvider provider;
    private final StockQuoteSnapshotMapper quoteMapper;
    private final MarketDataSyncTaskMapper taskMapper;
    private final MarketDataAlertMapper alertMapper;
    private final StockDailyBarMapper dailyBarMapper;
    private final TransactionTemplate txRequiresNew;
    private final ObjectMapper objectMapper;

    // ==================== Provider 状态 ====================

    public ProviderStatusVO getProviderStatus() {
        var hs = provider.healthCheck();
        return new ProviderStatusVO(provider.getProviderCode(), hs.configured(), hs.reachable(),
                hs.lastError(), hs.lastSuccessAt());
    }

    public ProviderStatusVO healthCheck() {
        return getProviderStatus();
    }

    // ==================== 最新价快照 ====================

    @Transactional
    public List<StockQuoteSnapshotVO> fetchLatestQuotes(FetchQuotesRequestDTO dto) {
        if (!provider.isConfigured()) {
            txRequiresNew.executeWithoutResult(s -> createAlert(
                    MarketDataConstants.ALERT_TYPE_PROVIDER_NOT_CONFIGURED,
                    MarketDataConstants.ALERT_SEVERITY_HIGH, null, null, null,
                    "行情 provider 未配置，获取最新行情请求被拒"));
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "行情 provider 未配置，无法获取最新行情");
        }
        List<String> symbols = dto.canonicalSymbols() != null ? dto.canonicalSymbols() : List.of();
        boolean persist = Boolean.TRUE.equals(dto.persist());

        List<ProviderQuote> quotes = provider.getLatestQuotes(symbols);
        List<StockQuoteSnapshotVO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ProviderQuote q : quotes) {
            StockQuoteSnapshotDO snapshot = StockQuoteSnapshotDO.builder()
                    .canonicalSymbol(q.canonicalSymbol()).quoteTime(q.quoteTime())
                    .currentPrice(q.currentPrice()).openPrice(q.openPrice()).highPrice(q.highPrice())
                    .lowPrice(q.lowPrice()).preClosePrice(q.preClosePrice())
                    .volume(q.volume() != null ? q.volume() : 0L).amount(q.amount())
                    .tradeStatus(q.tradeStatus()).dataSource(provider.getProviderCode())
                    .fetchedAt(now).build();
            if (persist) quoteMapper.upsert(snapshot);
            result.add(toVO(snapshot));
        }
        return result;
    }

    public PageResultVO<StockQuoteSnapshotVO> listSnapshots(String canonicalSymbol, String dataSource,
                                                              int page, int size) {
        StockDataService.validatePaging(page, size);
        int offset = (page - 1) * size;
        List<StockQuoteSnapshotDO> items = quoteMapper.selectByFilter(canonicalSymbol, dataSource, size, offset);
        long total = quoteMapper.countByFilter(canonicalSymbol, dataSource);
        return new PageResultVO<>(items.stream().map(this::toVO).toList(), total, page, size);
    }

    // ==================== 历史日 K 同步 ====================

    /**
     * 创建并执行日 K 同步任务。
     * <p>
     * 事务策略：任务创建、状态更新、alert 均用 REQUIRES_NEW 独立事务，
     * 保证即使 provider 调用失败或外层回滚，DB 仍可查到任务状态和 alert。
     */
    public MarketDataSyncTaskVO createAndExecuteDailyBarSync(CreateSyncTaskDTO dto) {
        // 结构化 scope -> JSON（Jackson 序列化）
        Map<String, Object> scopeMap = new LinkedHashMap<>();
        scopeMap.put("canonicalSymbol", dto.canonicalSymbol());
        scopeMap.put("startDate", dto.startDate());
        scopeMap.put("endDate", dto.endDate());
        scopeMap.put("adjustType", dto.adjustType());
        String scopeJson;
        try {
            scopeJson = objectMapper.writeValueAsString(scopeMap);
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "scope 序列化失败: " + e.getMessage());
        }

        String idemKey = UUID.nameUUIDFromBytes(
                (dto.provider() + dto.taskType() + scopeJson).getBytes()).toString();

        // 幂等：已有任务直接返回
        MarketDataSyncTaskDO existing = taskMapper.selectByIdempotencyKey(idemKey);
        if (existing != null) return toTaskVO(existing);

        // 1. 创建 PENDING 任务（独立事务）
        MarketDataSyncTaskDO task = txRequiresNew.execute(status -> {
            MarketDataSyncTaskDO t = MarketDataSyncTaskDO.builder()
                    .taskType(dto.taskType()).provider(dto.provider()).scopeJson(scopeJson)
                    .status(MarketDataConstants.TASK_STATUS_PENDING).idempotencyKey(idemKey)
                    .build();
            taskMapper.insert(t);
            return t;
        });

        Long taskId = task.getId();

        // 2. 检查 provider 配置
        if (!provider.isConfigured()) {
            updateTaskFailed(taskId, ErrorCodeEnum.BUSINESS_RULE_VIOLATION.getCode(),
                    "行情 provider 未配置", null);
            createAlertInNewTx(MarketDataConstants.ALERT_TYPE_PROVIDER_NOT_CONFIGURED,
                    MarketDataConstants.ALERT_SEVERITY_HIGH, dto.canonicalSymbol(), null, taskId,
                    "行情 provider 未配置，同步任务无法执行");
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "行情 provider 未配置，同步任务无法执行");
        }

        // 3. 转 RUNNING（独立事务）
        txRequiresNew.executeWithoutResult(status -> {
            MarketDataSyncTaskDO running = new MarketDataSyncTaskDO();
            running.setId(taskId);
            running.setStatus(MarketDataConstants.TASK_STATUS_RUNNING);
            running.setStartedAt(LocalDateTime.now());
            taskMapper.updateById(running);
        });

        // 4. 执行同步（不包事务，单条写入各自独立）
        SyncResult sr;
        try {
            sr = executeDailyBarSync(dto);
        } catch (BusinessException e) {
            updateTaskFailed(taskId, e.getErrorCode().getCode(), e.getMessage(), null);
            createAlertInNewTx(MarketDataConstants.ALERT_TYPE_SYNC_FAILED,
                    MarketDataConstants.ALERT_SEVERITY_HIGH, dto.canonicalSymbol(), null, taskId,
                    "日 K 同步失败: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            updateTaskFailed(taskId, ErrorCodeEnum.INTERNAL_ERROR.getCode(), e.getMessage(),
                    "{\"error\":\"" + e.getMessage() + "\"}");
            createAlertInNewTx(MarketDataConstants.ALERT_TYPE_SYNC_FAILED,
                    MarketDataConstants.ALERT_SEVERITY_HIGH, dto.canonicalSymbol(), null, taskId,
                    "日 K 同步异常: " + e.getMessage());
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "日 K 同步异常: " + e.getMessage());
        }

        // 5. 更新 SUCCEEDED/PARTIAL_FAILED（独立事务）
        txRequiresNew.executeWithoutResult(status -> {
            MarketDataSyncTaskDO done = new MarketDataSyncTaskDO();
            done.setId(taskId);
            done.setStatus(sr.failed > 0
                    ? MarketDataConstants.TASK_STATUS_PARTIAL_FAILED
                    : MarketDataConstants.TASK_STATUS_SUCCEEDED);
            done.setTotalCount(sr.total());
            done.setSuccessCount(sr.success());
            done.setFailCount(sr.failed());
            done.setInsertedCount(sr.inserted());
            done.setUpdatedCount(sr.updated());
            done.setSkippedCount(sr.skipped());
            done.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(done);
        });

        // 空数据提醒
        if (sr.total > 0 && sr.inserted == 0 && sr.updated == 0 && sr.skipped == sr.total) {
            createAlertInNewTx(MarketDataConstants.ALERT_TYPE_EMPTY_DAILY_BARS,
                    MarketDataConstants.ALERT_SEVERITY_INFO, dto.canonicalSymbol(), null, taskId,
                    "同步完成但未写入新数据，可能数据已存在或范围为空");
        }

        log.info("Daily bar sync task {} completed: total={}, inserted={}, updated={}, skipped={}, failed={}",
                taskId, sr.total, sr.inserted, sr.updated, sr.skipped, sr.failed);
        return toTaskVO(taskMapper.selectById(taskId));
    }

    /**
     * 实际执行日 K 同步：调用 provider → 逐条幂等写入。
     * 不使用事务包裹，单条写入各自独立（保证部分失败也能保留已成功数据）。
     */
    private SyncResult executeDailyBarSync(CreateSyncTaskDTO dto) {
        String adjustType = (dto.adjustType() == null || dto.adjustType().isBlank())
                ? "NONE" : dto.adjustType();
        LocalDate startDate = dto.startDate() != null ? dto.startDate() : LocalDate.now().minusMonths(1);
        LocalDate endDate = dto.endDate() != null ? dto.endDate() : LocalDate.now();

        List<ProviderDailyBar> bars = provider.getDailyBars(
                dto.canonicalSymbol(), startDate, endDate, adjustType);
        int total = bars.size(), inserted = 0, updated = 0, skipped = 0, failed = 0;

        for (ProviderDailyBar bar : bars) {
            try {
                StockDailyBarDO existing = dailyBarMapper.selectByUniqueKey(
                        bar.canonicalSymbol(), bar.tradeDate(), bar.adjustType(),
                        MarketDataConstants.DATA_SOURCE_LONGPORT);

                StockDailyBarDO DO = StockDailyBarDO.builder()
                        .canonicalSymbol(bar.canonicalSymbol()).tradeDate(bar.tradeDate())
                        .adjustType(bar.adjustType()).dataSource(MarketDataConstants.DATA_SOURCE_LONGPORT)
                        .openPrice(bar.openPrice()).highPrice(bar.highPrice())
                        .lowPrice(bar.lowPrice()).closePrice(bar.closePrice())
                        .volume(bar.volume() != null ? bar.volume() : 0L)
                        .amount(bar.amount() != null ? bar.amount() : java.math.BigDecimal.ZERO)
                        .fetchedAt(LocalDateTime.now()).build();

                if (existing == null) {
                    dailyBarMapper.insert(DO);
                    inserted++;
                } else if (isSameBar(existing, DO)) {
                    skipped++;
                } else {
                    dailyBarMapper.updateByUniqueKey(DO);
                    updated++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("Failed to sync bar {} {}: {}", dto.canonicalSymbol(), bar.tradeDate(), e.getMessage());
            }
        }
        return new SyncResult(total, total - failed, failed, inserted, updated, skipped);
    }

    private boolean isSameBar(StockDailyBarDO a, StockDailyBarDO b) {
        return a.getOpenPrice().compareTo(b.getOpenPrice()) == 0
                && a.getHighPrice().compareTo(b.getHighPrice()) == 0
                && a.getLowPrice().compareTo(b.getLowPrice()) == 0
                && a.getClosePrice().compareTo(b.getClosePrice()) == 0
                && a.getVolume() != null && a.getVolume().equals(b.getVolume());
    }

    /** 独立事务更新任务为 FAILED（同时设 startedAt 兜底，保证有时间范围）。 */
    private void updateTaskFailed(Long taskId, String errorCode, String errorMsg, String errorJson) {
        txRequiresNew.executeWithoutResult(status -> {
            MarketDataSyncTaskDO fail = new MarketDataSyncTaskDO();
            fail.setId(taskId);
            fail.setStatus(MarketDataConstants.TASK_STATUS_FAILED);
            fail.setStartedAt(LocalDateTime.now());
            fail.setFinishedAt(LocalDateTime.now());
            fail.setLastErrorCode(errorCode);
            fail.setTotalCount(0);
            fail.setSuccessCount(0);
            fail.setFailCount(0);
            fail.setInsertedCount(0);
            fail.setUpdatedCount(0);
            fail.setSkippedCount(0);
            if (errorJson != null) fail.setErrorSummaryJson(errorJson);
            taskMapper.updateById(fail);
        });
    }

    // ==================== 同步任务查询 ====================

    public PageResultVO<MarketDataSyncTaskVO> listSyncTasks(String status, String providerCode, int page, int size) {
        StockDataService.validatePaging(page, size);
        int offset = (page - 1) * size;
        List<MarketDataSyncTaskDO> items = taskMapper.selectByFilter(status, providerCode, size, offset);
        long total = taskMapper.countByFilter(status, providerCode);
        return new PageResultVO<>(items.stream().map(this::toTaskVO).toList(), total, page, size);
    }

    public MarketDataSyncTaskVO getSyncTask(Long id) {
        MarketDataSyncTaskDO t = taskMapper.selectById(id);
        if (t == null) throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "同步任务不存在: " + id);
        return toTaskVO(t);
    }

    // ==================== 异常提醒 ====================

    public PageResultVO<MarketDataAlertVO> listAlerts(Boolean resolved, String severity,
                                                       String symbol, int page, int size) {
        StockDataService.validatePaging(page, size);
        int offset = (page - 1) * size;
        List<MarketDataAlertDO> items = alertMapper.selectByFilter(resolved, severity, symbol, size, offset);
        long total = alertMapper.countByFilter(resolved, severity, symbol);
        return new PageResultVO<>(items.stream().map(this::toAlertVO).toList(), total, page, size);
    }

    @Transactional
    public MarketDataAlertVO resolveAlert(Long id) {
        MarketDataAlertDO a = alertMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "提醒不存在: " + id);
        alertMapper.updateResolved(id, true);
        a.setResolved(true);
        return toAlertVO(a);
    }

    /** 在当前事务中创建 alert。 */
    private void createAlert(String alertType, String severity, String canonicalSymbol,
                            LocalDateTime quoteTime, Long taskId, String message) {
        alertMapper.insert(MarketDataAlertDO.builder()
                .alertType(alertType).severity(severity)
                .canonicalSymbol(canonicalSymbol).quoteTime(quoteTime)
                .provider(provider.getProviderCode()).taskId(taskId)
                .message(message).resolved(false).build());
    }

    /** 在独立新事务中创建 alert（保证失败时仍留痕）。 */
    private void createAlertInNewTx(String alertType, String severity, String canonicalSymbol,
                                    LocalDateTime quoteTime, Long taskId, String message) {
        txRequiresNew.executeWithoutResult(status -> createAlert(
                alertType, severity, canonicalSymbol, quoteTime, taskId, message));
    }

    // ==================== 转换 ====================

    private StockQuoteSnapshotVO toVO(StockQuoteSnapshotDO d) {
        return new StockQuoteSnapshotVO(d.getId(), d.getCanonicalSymbol(), d.getQuoteTime(),
                d.getCurrentPrice(), d.getOpenPrice(), d.getHighPrice(), d.getLowPrice(),
                d.getPreClosePrice(), d.getVolume(), d.getAmount(), d.getTradeStatus(),
                d.getDataSource(), d.getFetchedAt());
    }

    private MarketDataSyncTaskVO toTaskVO(MarketDataSyncTaskDO t) {
        return new MarketDataSyncTaskVO(t.getId(), t.getTaskType(), t.getProvider(), t.getScopeJson(),
                t.getStatus(), t.getTotalCount(), t.getSuccessCount(), t.getFailCount(),
                t.getInsertedCount(), t.getUpdatedCount(), t.getSkippedCount(),
                t.getStartedAt(), t.getFinishedAt(), t.getLastErrorCode(), t.getErrorSummaryJson(),
                t.getCreatedAt());
    }

    private MarketDataAlertVO toAlertVO(MarketDataAlertDO a) {
        return new MarketDataAlertVO(a.getId(), a.getAlertType(), a.getSeverity(), a.getCanonicalSymbol(),
                a.getQuoteTime(), a.getTradeDate(), a.getProvider(), a.getTaskId(),
                a.getMessage(), a.getTriggerValueJson(), a.getResolved(), a.getCreatedAt());
    }

    /** 同步执行内部结果。 */
    private record SyncResult(int total, int success, int failed, int inserted, int updated, int skipped) {}
}
