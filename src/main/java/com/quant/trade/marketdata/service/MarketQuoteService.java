package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dao.*;
import com.quant.trade.marketdata.model.*;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderQuote;
import com.quant.trade.marketdata.vo.MarketDataVOs.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** 行情服务：provider 状态、最新价快照、同步任务、异常提醒。 */
@Slf4j @Service @RequiredArgsConstructor
public class MarketQuoteService {
    private final MarketDataProvider provider;
    private final StockQuoteSnapshotMapper quoteMapper;
    private final MarketDataSyncTaskMapper taskMapper;
    private final MarketDataAlertMapper alertMapper;

    public ProviderStatusVO getProviderStatus() {
        var hs = provider.healthCheck();
        return new ProviderStatusVO(provider.getProviderCode(), hs.configured(), hs.reachable(), hs.lastError(), hs.lastSuccessAt());
    }

    public ProviderStatusVO healthCheck() {
        return getProviderStatus();
    }

    @Transactional
    public List<StockQuoteSnapshotVO> fetchLatestQuotes(List<String> canonicalSymbols, boolean persist) {
        if (!provider.isConfigured()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "行情 provider 未配置，无法获取最新行情");
        }
        List<ProviderQuote> quotes = provider.getLatestQuotes(canonicalSymbols);
        List<StockQuoteSnapshotVO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ProviderQuote q : quotes) {
            StockQuoteSnapshotDO DO = StockQuoteSnapshotDO.builder()
                    .canonicalSymbol(q.canonicalSymbol()).quoteTime(q.quoteTime())
                    .currentPrice(q.currentPrice()).openPrice(q.openPrice()).highPrice(q.highPrice())
                    .lowPrice(q.lowPrice()).preClosePrice(q.preClosePrice())
                    .volume(q.volume() != null ? q.volume() : 0L).amount(q.amount())
                    .tradeStatus(q.tradeStatus()).dataSource(provider.getProviderCode())
                    .fetchedAt(now).build();
            if (persist) quoteMapper.upsert(DO);
            result.add(toVO(DO));
        }
        return result;
    }

    public Map<String, Object> listSnapshots(String canonicalSymbol, String dataSource, int page, int size) {
        StockDataService.validatePaging(page, size);
        int offset = (page - 1) * size;
        List<StockQuoteSnapshotDO> items = quoteMapper.selectByFilter(canonicalSymbol, dataSource, size, offset);
        long total = quoteMapper.countByFilter(canonicalSymbol, dataSource);
        return Map.of("items", items.stream().map(this::toVO).toList(), "total", total, "page", page, "size", size);
    }

    @Transactional
    public MarketDataSyncTaskVO createSyncTask(CreateSyncTaskDTO dto) {
        String idemKey = UUID.nameUUIDFromBytes((dto.provider() + dto.taskType() + dto.scopeJson()).getBytes()).toString();
        MarketDataSyncTaskDO existing = taskMapper.selectByIdempotencyKey(idemKey);
        if (existing != null) return toTaskVO(existing);
        MarketDataSyncTaskDO task = MarketDataSyncTaskDO.builder()
                .taskType(dto.taskType()).provider(dto.provider()).scopeJson(dto.scopeJson())
                .status("PENDING").idempotencyKey(idemKey).build();
        taskMapper.insert(task);
        return toTaskVO(task);
    }

    public Map<String, Object> listSyncTasks(String status, String providerCode, int page, int size) {
        StockDataService.validatePaging(page, size);
        int offset = (page - 1) * size;
        List<MarketDataSyncTaskDO> items = taskMapper.selectByFilter(status, providerCode, size, offset);
        long total = taskMapper.countByFilter(status, providerCode);
        return Map.of("items", items.stream().map(this::toTaskVO).toList(), "total", total, "page", page, "size", size);
    }

    public MarketDataSyncTaskVO getSyncTask(Long id) {
        MarketDataSyncTaskDO t = taskMapper.selectById(id);
        if (t == null) throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "同步任务不存在: " + id);
        return toTaskVO(t);
    }

    public Map<String, Object> listAlerts(Boolean resolved, String severity, String symbol, int page, int size) {
        StockDataService.validatePaging(page, size);
        int offset = (page - 1) * size;
        List<MarketDataAlertDO> items = alertMapper.selectByFilter(resolved, severity, symbol, size, offset);
        long total = alertMapper.countByFilter(resolved, severity, symbol);
        return Map.of("items", items.stream().map(this::toAlertVO).toList(), "total", total, "page", page, "size", size);
    }

    @Transactional
    public MarketDataAlertVO resolveAlert(Long id) {
        MarketDataAlertDO a = alertMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "提醒不存在: " + id);
        alertMapper.updateResolved(id, true);
        a.setResolved(true);
        return toAlertVO(a);
    }

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
                t.getStartedAt(), t.getFinishedAt(), t.getLastErrorCode(), t.getErrorSummaryJson(), t.getCreatedAt());
    }
    private MarketDataAlertVO toAlertVO(MarketDataAlertDO a) {
        return new MarketDataAlertVO(a.getId(), a.getAlertType(), a.getSeverity(), a.getCanonicalSymbol(),
                a.getQuoteTime(), a.getTradeDate(), a.getProvider(), a.getTaskId(),
                a.getMessage(), a.getTriggerValueJson(), a.getResolved(), a.getCreatedAt());
    }
}
