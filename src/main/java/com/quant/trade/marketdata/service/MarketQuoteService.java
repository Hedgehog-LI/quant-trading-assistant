package com.quant.trade.marketdata.service;

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
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 获取最新行情并可选落库。
     *
     * @return 快照 VO 列表
     */
    @Transactional
    public List<StockQuoteSnapshotVO> fetchLatestQuotes(FetchQuotesRequestDTO dto) {
        if (!provider.isConfigured()) {
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
            if (persist) {
                quoteMapper.upsert(snapshot);
            }
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
     * 创建并立即执行日 K 同步任务。
     * <p>
     * 流程：创建 PENDING → RUNNING → 调用 provider → 逐条幂等写入 stock_daily_bar
     * → 更新 SUCCEEDED/FAILED + inserted/updated/skipped/failed 计数。
     *
     * @return 同步任务 VO（含执行结果）
     */
    @Transactional
    public MarketDataSyncTaskVO createAndExecuteDailyBarSync(CreateSyncTaskDTO dto) {
        if (!provider.isConfigured()) {
            createAlert(MarketDataConstants.ALERT_TYPE_PROVIDER_NOT_CONFIGURED,
                    MarketDataConstants.ALERT_SEVERITY_HIGH, null, null, null,
                    "行情 provider 未配置，同步任务无法执行");
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "行情 provider 未配置，同步任务无法执行");
        }

        // 幂等检查
        String idemKey = UUID.nameUUIDFromBytes(
                (dto.provider() + dto.taskType() + dto.scopeJson()).getBytes()).toString();
        MarketDataSyncTaskDO existing = taskMapper.selectByIdempotencyKey(idemKey);
        if (existing != null) {
            return toTaskVO(existing);
        }

        // 创建任务 PENDING
        MarketDataSyncTaskDO task = MarketDataSyncTaskDO.builder()
                .taskType(dto.taskType()).provider(dto.provider()).scopeJson(dto.scopeJson())
                .status(MarketDataConstants.TASK_STATUS_PENDING).idempotencyKey(idemKey)
                .build();
        taskMapper.insert(task);

        // 转 RUNNING
        task.setStatus(MarketDataConstants.TASK_STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        // 执行同步
        try {
            SyncResult sr = executeDailyBarSync(dto.scopeJson());
            task.setTotalCount(sr.total);
            task.setSuccessCount(sr.success);
            task.setFailCount(sr.failed);
            task.setInsertedCount(sr.inserted);
            task.setUpdatedCount(sr.updated);
            task.setSkippedCount(sr.skipped);
            task.setStatus(sr.failed > 0
                    ? MarketDataConstants.TASK_STATUS_PARTIAL_FAILED
                    : MarketDataConstants.TASK_STATUS_SUCCEEDED);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            // 空数据提醒
            if (sr.total > 0 && sr.inserted == 0 && sr.updated == 0 && sr.skipped == sr.total) {
                createAlert(MarketDataConstants.ALERT_TYPE_EMPTY_DAILY_BARS,
                        MarketDataConstants.ALERT_SEVERITY_INFO, null, null, task.getId(),
                        "同步完成但未写入新数据，可能数据已存在或范围为空");
            }

            log.info("Daily bar sync task {} completed: total={}, inserted={}, updated={}, skipped={}, failed={}",
                    task.getId(), sr.total, sr.inserted, sr.updated, sr.skipped, sr.failed);
        } catch (BusinessException e) {
            task.setStatus(MarketDataConstants.TASK_STATUS_FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setLastErrorCode(e.getErrorCode().getCode());
            taskMapper.updateById(task);
            createAlert(MarketDataConstants.ALERT_TYPE_SYNC_FAILED,
                    MarketDataConstants.ALERT_SEVERITY_HIGH, null, null, task.getId(),
                    "日 K 同步失败: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            task.setStatus(MarketDataConstants.TASK_STATUS_FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setLastErrorCode(ErrorCodeEnum.INTERNAL_ERROR.getCode());
            task.setErrorSummaryJson("{\"error\":\"" + e.getMessage() + "\"}");
            taskMapper.updateById(task);
            createAlert(MarketDataConstants.ALERT_TYPE_SYNC_FAILED,
                    MarketDataConstants.ALERT_SEVERITY_HIGH, null, null, task.getId(),
                    "日 K 同步异常: " + e.getMessage());
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "日 K 同步异常: " + e.getMessage());
        }

        return toTaskVO(taskMapper.selectById(task.getId()));
    }

    /**
     * 实际执行日 K 同步逻辑：解析 scope → 调 provider → 逐条幂等写入。
     */
    private SyncResult executeDailyBarSync(String scopeJson) {
        // 简化 scope 解析：期望格式 {"canonicalSymbol":"SH.600519","startDate":"2026-06-01","endDate":"2026-07-01","adjustType":"NONE"}
        // 此处用简单字符串解析避免引入 JSON 库
        String symbol = extractField(scopeJson, "canonicalSymbol");
        String startDateStr = extractField(scopeJson, "startDate");
        String endDateStr = extractField(scopeJson, "endDate");
        String adjustType = extractField(scopeJson, "adjustType");
        if (adjustType == null || adjustType.isBlank()) adjustType = "NONE";

        if (symbol == null || symbol.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "scope 中 canonicalSymbol 不能为空");
        }

        LocalDate startDate = startDateStr != null && !startDateStr.isBlank()
                ? LocalDate.parse(startDateStr) : LocalDate.now().minusMonths(1);
        LocalDate endDate = endDateStr != null && !endDateStr.isBlank()
                ? LocalDate.parse(endDateStr) : LocalDate.now();

        List<ProviderDailyBar> bars = provider.getDailyBars(symbol, startDate, endDate, adjustType);
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
                log.warn("Failed to sync bar {} {}: {}", symbol, bar.tradeDate(), e.getMessage());
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

    /** 从简单 JSON 字符串中提取字段值（避免引入 Jackson 到此方法）。 */
    private String extractField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int startQuote = json.indexOf('"', colon + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
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

    /** 内部方法：创建异常提醒。 */
    private void createAlert(String alertType, String severity, String canonicalSymbol,
                            LocalDateTime quoteTime, Long taskId, String message) {
        MarketDataAlertDO alert = MarketDataAlertDO.builder()
                .alertType(alertType).severity(severity)
                .canonicalSymbol(canonicalSymbol).quoteTime(quoteTime)
                .provider(provider.getProviderCode()).taskId(taskId)
                .message(message).resolved(false).build();
        alertMapper.insert(alert);
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
