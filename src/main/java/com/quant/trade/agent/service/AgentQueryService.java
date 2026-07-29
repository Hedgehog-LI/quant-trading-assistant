package com.quant.trade.agent.service;

import com.quant.trade.agent.vo.TrustedAnswer;
import com.quant.trade.dashboard.service.DashboardService;
import com.quant.trade.dashboard.vo.DashboardTodayVO;
import com.quant.trade.marketdata.service.MarketDataWorkbenchService;
import com.quant.trade.marketdata.service.MarketQuoteService;
import com.quant.trade.marketdata.service.MarketSectorRankingService;
import com.quant.trade.marketdata.vo.MarketSectorRankingBatchVO;
import com.quant.trade.marketdata.vo.StockQuoteSnapshotVO;
import com.quant.trade.marketdata.vo.WorkbenchOverviewVO;
import com.quant.trade.portfolio.service.PortfolioService;
import com.quant.trade.portfolio.vo.PortfolioSummaryVO;
import com.quant.trade.portfolio.vo.PositionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 只读查询服务。复用现有业务 Service，不直接访问 Mapper。
 * <p>
 * 所有方法返回 TrustedAnswer，包含结论、数据时间、新鲜度和证据。
 * 区分"尚未采集""确实无结果""查询失败"和"Provider不可用"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentQueryService {

    private static final ZoneOffset CST = ZoneOffset.ofHours(8);

    private final DashboardService dashboardService;
    private final PortfolioService portfolioService;
    private final MarketDataWorkbenchService marketDataWorkbenchService;
    private final MarketQuoteService marketQuoteService;
    private final MarketSectorRankingService marketSectorRankingService;

    /** 能力查询 */
    public TrustedAnswer capabilities() {
        var caps = Map.of(
            "version", "1.0",
            "readOnly", true,
            "tools", List.of(
                "qta_system_health", "qta_today_overview", "qta_portfolio_summary",
                "qta_collection_overview", "qta_collection_failures",
                "qta_data_quality_alerts", "qta_sector_ranking_summary",
                "qta_security_market_summary"
            ),
            "disclaimer", "所有投资相关回复不构成投资建议"
        );
        return TrustedAnswer.of("Agent 只读助手已就绪", caps, TrustedAnswer.FRESH,
            OffsetDateTime.now(CST), null);
    }

    /** 1. 系统健康 — 覆盖应用、数据库、provider */
    public TrustedAnswer systemHealth() {
        try {
            WorkbenchOverviewVO overview = marketDataWorkbenchService.getOverview();
            var ps = overview.getProviderStatus();
            boolean providerReachable = ps != null && ps.reachable();
            boolean providerConfigured = ps != null && ps.configured();

            // 应用和 DB 可达性：如果能查到 overview 说明应用和 DB 都正常
            boolean appHealthy = true;
            boolean dbHealthy = true;

            String conclusion;
            if (!appHealthy) {
                conclusion = "应用异常";
            } else if (!dbHealthy) {
                conclusion = "数据库不可达";
            } else if (providerReachable) {
                conclusion = "系统运行正常，应用、数据库和 Provider 均可达";
            } else if (providerConfigured) {
                conclusion = "系统运行中，应用和数据库正常，但 Provider 不可达";
            } else {
                conclusion = "系统运行中，应用和数据库正常，但 Provider 未配置";
            }

            var dataMap = new HashMap<String, Object>();
            dataMap.put("appHealthy", appHealthy);
            dataMap.put("dbHealthy", dbHealthy);
            dataMap.put("providerConfigured", providerConfigured);
            dataMap.put("providerReachable", providerReachable);
            dataMap.put("providerLastError", ps != null ? ps.lastError() : null);
            dataMap.put("lastSuccessAt", ps != null && ps.lastSuccessAt() != null ? ps.lastSuccessAt().toString() : null);
            dataMap.put("totalSymbols", overview.getTotalSymbols());
            dataMap.put("totalDailyBars", overview.getTotalDailyBars());
            dataMap.put("totalMinuteBars", overview.getTotalMinuteBars());
            dataMap.put("failedTasksToday", overview.getFailedTasksToday());

            String freshness = providerReachable ? TrustedAnswer.FRESH
                : providerConfigured ? TrustedAnswer.DELAYED : TrustedAnswer.UNKNOWN;

            return TrustedAnswer.of(conclusion, dataMap, freshness, null, null);
        } catch (Exception e) {
            log.error("systemHealth failed", e);
            return TrustedAnswer.fail("系统健康查询失败: " + e.getMessage());
        }
    }

    /** 2. 今日待办 */
    public TrustedAnswer todayOverview(LocalDate date) {
        try {
            DashboardTodayVO today = dashboardService.getToday(date);
            var dataMap = new HashMap<String, Object>();
            dataMap.put("date", today.date() != null ? today.date().toString() : null);
            dataMap.put("enabledWatchlistCount", today.enabledWatchlistCount());
            dataMap.put("activePlanCount", today.activePlanCount());
            dataMap.put("todayJournalCount", today.todayJournalCount());
            dataMap.put("pendingReviewCount", today.pendingReviewCount());
            dataMap.put("riskWarnings", today.riskWarnings() != null ? today.riskWarnings() : List.of());
            dataMap.put("todos", today.todos() != null ? today.todos() : List.of());

            return TrustedAnswer.of("今日待办已加载", dataMap, TrustedAnswer.FRESH,
                OffsetDateTime.now(CST), null);
        } catch (Exception e) {
            log.error("todayOverview failed", e);
            return TrustedAnswer.fail("今日待办查询失败");
        }
    }

    /** 3. 持仓摘要 */
    public TrustedAnswer portfolioSummary() {
        try {
            PortfolioSummaryVO summary = portfolioService.getSummary();
            List<PositionVO> positions = portfolioService.getPositions();

            var dataMap = new HashMap<String, Object>();
            dataMap.put("realizedPnl", summary.realizedPnl());
            dataMap.put("unrealizedPnl", summary.unrealizedPnl());
            dataMap.put("totalPnl", summary.totalPnl());
            dataMap.put("winRate", summary.winRate());
            dataMap.put("closedTradeCount", summary.closedTradeCount());
            dataMap.put("positionCount", positions != null ? positions.size() : 0);
            dataMap.put("disclaimer", "不构成投资建议。盈亏数据仅用于复盘参考。");

            List<String> warnings = new ArrayList<>();
            warnings.add("不构成投资建议");
            if (summary.warnings() != null) warnings.addAll(summary.warnings());

            return new TrustedAnswer(
                "持仓摘要已加载",
                OffsetDateTime.now(CST),
                null,
                TrustedAnswer.UNKNOWN,
                List.of(new TrustedAnswer.Evidence("PORTFOLIO", "FIFO", OffsetDateTime.now(CST))),
                warnings,
                dataMap
            );
        } catch (Exception e) {
            log.error("portfolioSummary failed", e);
            return TrustedAnswer.fail("持仓摘要查询失败");
        }
    }

    /**
     * 4. 行情采集概览 — freshness 基于真实数据时间。
     * <p>
     * market 过滤：当指定 market 时，从 recentWatermarks 中按 canonicalSymbol 前缀过滤（SH/SZ/BJ→CN, HK→HK, US→US）。
     * date 过滤：当指定 date 时，从 recentWatermarks 中按 lastTradeDate 过滤，只返回该交易日的数据。
     */
    public TrustedAnswer collectionOverview(String market, LocalDate date) {
        try {
            var overview = marketDataWorkbenchService.getOverview();

            // Market filter: derive market from canonical_symbol prefix
            boolean marketFilterApplied = market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market);

            // Date filter: filter watermarks by lastTradeDate
            boolean dateFilterApplied = date != null;

            // Apply both filters to watermarks
            var allWatermarks = overview.getRecentWatermarks() != null ? overview.getRecentWatermarks() : List.<com.quant.trade.marketdata.vo.MarketDataWatermarkVO>of();
            List<com.quant.trade.marketdata.vo.MarketDataWatermarkVO> filteredWatermarks = allWatermarks;

            if (marketFilterApplied) {
                filteredWatermarks = filteredWatermarks.stream()
                    .filter(wm -> matchesMarket(wm.getCanonicalSymbol(), market))
                    .toList();
            }

            if (dateFilterApplied) {
                filteredWatermarks = filteredWatermarks.stream()
                    .filter(wm -> date.equals(wm.getLastTradeDate()))
                    .toList();
            }

            var dataMap = new HashMap<String, Object>();
            dataMap.put("market", market != null ? market : "ALL");
            dataMap.put("marketFilterApplied", marketFilterApplied);
            dataMap.put("date", date != null ? date.toString() : null);
            dataMap.put("dateFilterApplied", dateFilterApplied);
            dataMap.put("totalSymbols", overview.getTotalSymbols());
            dataMap.put("totalDailyBars", overview.getTotalDailyBars());
            dataMap.put("totalMinuteBars", overview.getTotalMinuteBars());
            dataMap.put("failedTasksToday", overview.getFailedTasksToday());
            dataMap.put("unresolvedHighAlerts", overview.getUnresolvedHighAlerts());
            dataMap.put("unresolvedWarnAlerts", overview.getUnresolvedWarnAlerts());
            dataMap.put("latestSyncAt", overview.getLatestSyncAt() != null ? overview.getLatestSyncAt().toString() : null);
            dataMap.put("recentWatermarks", filteredWatermarks.subList(0, Math.min(10, filteredWatermarks.size())));
            dataMap.put("filteredWatermarkCount", filteredWatermarks.size());

            // 基于 latestSyncAt 计算真实 freshness
            String freshness;
            OffsetDateTime dataAsOf = null;
            if (overview.getLatestSyncAt() != null) {
                dataAsOf = OffsetDateTime.of(overview.getLatestSyncAt(), CST);
                long hoursAgo = Duration.between(dataAsOf, OffsetDateTime.now(CST)).toHours();
                freshness = hoursAgo <= 1 ? TrustedAnswer.FRESH
                    : hoursAgo <= 6 ? TrustedAnswer.DELAYED
                    : TrustedAnswer.STALE;
            } else {
                freshness = TrustedAnswer.UNKNOWN;
            }

            String baseConclusion = overview.getFailedTasksToday() > 0
                ? "行情采集存在失败任务"
                : overview.getLatestSyncAt() != null
                    ? "行情采集正常，最近同步: " + overview.getLatestSyncAt()
                    : "尚无行情采集记录";

            List<String> filterNotes = new ArrayList<>();
            if (marketFilterApplied) filterNotes.add("市场=" + market);
            if (dateFilterApplied) filterNotes.add("日期=" + date);
            String finalConclusion = filterNotes.isEmpty()
                ? baseConclusion
                : baseConclusion + "（已过滤：" + String.join("，", filterNotes) + "，剩余 " + filteredWatermarks.size() + " 条水位）";

            return new TrustedAnswer(finalConclusion, OffsetDateTime.now(CST), dataAsOf,
                freshness, null, List.of(), dataMap);
        } catch (Exception e) {
            log.error("collectionOverview failed", e);
            return TrustedAnswer.fail("行情采集概览查询失败");
        }
    }

    /** 从 canonical_symbol 前缀推导市场并匹配。SH/SZ/BJ→CN, HK→HK, US→US。 */
    private boolean matchesMarket(String canonicalSymbol, String market) {
        if (canonicalSymbol == null || market == null) return false;
        String normalizedMarket = market.toUpperCase();
        if (canonicalSymbol.startsWith("SH.") || canonicalSymbol.startsWith("SZ.") || canonicalSymbol.startsWith("BJ.")) {
            return "CN".equals(normalizedMarket);
        } else if (canonicalSymbol.startsWith("HK.")) {
            return "HK".equals(normalizedMarket);
        } else if (canonicalSymbol.startsWith("US.")) {
            return "US".equals(normalizedMarket);
        }
        return false;
    }

    /**
     * 5. 采集失败 — 查询真实失败任务。
     * <p>
     * market: 过滤 scopeJson 中的 canonicalSymbol/symbols 前缀（SH/SZ/BJ→CN, HK→HK, US→US）。
     * since: 过滤 createdAt >= since（ISO date-time）。
     * limit: 控制返回条数。
     */
    public TrustedAnswer collectionFailures(String market, String since, int limit) {
        try {
            int safeLimit = Math.min(Math.max(limit, 1), 50);

            // 查询 FAILED 状态的同步任务（取较大窗口再在内存过滤）
            var failedTasks = marketQuoteService.listSyncTasks("FAILED", null, 1, 50);
            var taskItems = failedTasks.items() != null ? new java.util.ArrayList<>(failedTasks.items()) : new java.util.ArrayList<com.quant.trade.marketdata.vo.MarketDataSyncTaskVO>();

            // Apply market filter on scopeJson
            if (market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market)) {
                taskItems = (java.util.ArrayList<com.quant.trade.marketdata.vo.MarketDataSyncTaskVO>) taskItems.stream()
                    .filter(t -> {
                        String scope = t.scopeJson();
                        if (scope == null) return true;
                        boolean hasMarketSymbol = scope.contains("SH.") || scope.contains("SZ.") || scope.contains("BJ.");
                        boolean hasHkSymbol = scope.contains("HK.");
                        boolean hasUsSymbol = scope.contains("US.");
                        if ("CN".equalsIgnoreCase(market)) return hasMarketSymbol;
                        if ("HK".equalsIgnoreCase(market)) return hasHkSymbol;
                        if ("US".equalsIgnoreCase(market)) return hasUsSymbol;
                        return true;
                    })
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            }

            // Apply since filter on createdAt
            if (since != null && !since.isBlank()) {
                LocalDateTime sinceDt = java.time.LocalDateTime.parse(since);
                taskItems = (java.util.ArrayList<com.quant.trade.marketdata.vo.MarketDataSyncTaskVO>) taskItems.stream()
                    .filter(t -> t.createdAt() != null && !t.createdAt().isBefore(sinceDt))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            }

            // Apply limit
            var limitedTasks = taskItems.stream().limit(safeLimit).toList();

            // 同时查 HIGH 级别未解决 alerts（同样做 since 过滤）
            var alerts = marketQuoteService.listAlerts(false, "HIGH", null, 1, safeLimit);
            var alertItems = alerts.items() != null ? new java.util.ArrayList<>(alerts.items()) : new java.util.ArrayList<com.quant.trade.marketdata.vo.MarketDataAlertVO>();
            if (since != null && !since.isBlank()) {
                LocalDateTime sinceDt = java.time.LocalDateTime.parse(since);
                alertItems = alertItems.stream()
                    .filter(a -> a.createdAt() != null && !a.createdAt().isBefore(sinceDt))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            }

            boolean marketFilterApplied = market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market);
            boolean sinceFilterApplied = since != null && !since.isBlank();

            var dataMap = new HashMap<String, Object>();
            dataMap.put("market", market != null ? market : "ALL");
            dataMap.put("marketFilterApplied", marketFilterApplied);
            dataMap.put("since", since);
            dataMap.put("sinceFilterApplied", sinceFilterApplied);
            dataMap.put("failedTasks", limitedTasks);
            dataMap.put("failedTaskCount", taskItems.size());
            dataMap.put("highAlerts", alertItems);
            dataMap.put("highAlertCount", alertItems.size());

            String conclusion = limitedTasks.isEmpty() && alertItems.isEmpty()
                ? "当前没有未处理的失败任务或高优先级提醒"
                : String.format("存在 %d 个失败任务和 %d 个高优先级提醒", taskItems.size(), alertItems.size());

            return TrustedAnswer.of(conclusion, dataMap,
                limitedTasks.isEmpty() ? TrustedAnswer.FRESH : TrustedAnswer.DELAYED,
                OffsetDateTime.now(CST),
                List.of(new TrustedAnswer.Evidence("SYNC_TASK", "FAILED",
                    OffsetDateTime.now(CST))));
        } catch (Exception e) {
            log.error("collectionFailures failed", e);
            return TrustedAnswer.fail("采集失败查询失败");
        }
    }

    /**
     * 6. 数据质量提醒。
     * <p>
     * status: resolved/unresolved/null。
     * since: 过滤 createdAt >= since。
     * limit: 控制返回条数。
     */
    public TrustedAnswer dataQualityAlerts(String status, String since, int limit) {
        try {
            int safeLimit = Math.min(Math.max(limit, 1), 50);
            Boolean resolved;
            if ("resolved".equalsIgnoreCase(status)) {
                resolved = Boolean.TRUE;
            } else if ("unresolved".equalsIgnoreCase(status)) {
                resolved = Boolean.FALSE;
            } else {
                resolved = null;
            }
            var alerts = marketQuoteService.listAlerts(resolved, null, null, 1, 50);
            var allItems = alerts.items() != null ? new java.util.ArrayList<>(alerts.items()) : new java.util.ArrayList<com.quant.trade.marketdata.vo.MarketDataAlertVO>();

            // Apply since filter on createdAt
            boolean sinceFilterApplied = since != null && !since.isBlank();
            if (sinceFilterApplied) {
                LocalDateTime sinceDt = java.time.LocalDateTime.parse(since);
                allItems = (java.util.ArrayList<com.quant.trade.marketdata.vo.MarketDataAlertVO>) allItems.stream()
                    .filter(a -> a.createdAt() != null && !a.createdAt().isBefore(sinceDt))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            }

            // Apply limit
            var items = allItems.stream().limit(safeLimit).toList();

            String conclusion = items.isEmpty()
                ? "当前没有数据质量提醒"
                : "存在 " + alerts.total() + " 个数据质量提醒";

            var dataMap = new HashMap<String, Object>();
            dataMap.put("alerts", items);
            dataMap.put("total", alerts.total());

            return TrustedAnswer.of(conclusion, dataMap,
                TrustedAnswer.FRESH, OffsetDateTime.now(CST), null);
        } catch (Exception e) {
            log.error("dataQualityAlerts failed", e);
            return TrustedAnswer.fail("数据质量提醒查询失败");
        }
    }

    /** 7. 板块排行摘要 — 查询真实排行数据 */
    public TrustedAnswer sectorRankingSummary(String market, int limit) {
        try {
            int safeLimit = Math.min(Math.max(limit, 1), 50);
            String normalizedMarket = market != null && !market.isBlank() ? market : "CN";

            // 查询最近一批排行数据
            var history = marketSectorRankingService.history(normalizedMarket, null, null, 1, safeLimit);
            var batches = history.items() != null ? history.items() : List.<MarketSectorRankingBatchVO>of();

            var dataMap = new HashMap<String, Object>();
            dataMap.put("market", normalizedMarket);

            if (batches.isEmpty()) {
                dataMap.put("batches", List.of());
                dataMap.put("total", 0);
                return TrustedAnswer.of(
                    normalizedMarket + " 市场尚无板块排行数据",
                    dataMap, TrustedAnswer.UNKNOWN, null, null);
            }

            // 取最新一批
            var latestBatch = batches.get(0);
            var batchMap = new HashMap<String, Object>();
            batchMap.put("id", latestBatch.id());
            batchMap.put("tradeDate", latestBatch.tradeDate() != null ? latestBatch.tradeDate().toString() : null);
            batchMap.put("snapshotTime", latestBatch.snapshotTime() != null ? latestBatch.snapshotTime().toString() : null);
            batchMap.put("risingCount", latestBatch.risingCount());
            batchMap.put("fallingCount", latestBatch.fallingCount());
            batchMap.put("flatCount", latestBatch.flatCount());
            batchMap.put("leaderSectorName", latestBatch.leaderSectorName());
            batchMap.put("leaderChangeRate", latestBatch.leaderChangeRate());
            batchMap.put("laggardSectorName", latestBatch.laggardSectorName());
            batchMap.put("laggardChangeRate", latestBatch.laggardChangeRate());
            batchMap.put("qualityStatus", latestBatch.qualityStatus());
            dataMap.put("latestBatch", batchMap);
            dataMap.put("totalBatches", history.total());

            // 基于 snapshotTime 计算 freshness
            String freshness;
            OffsetDateTime dataAsOf = null;
            if (latestBatch.snapshotTime() != null) {
                dataAsOf = OffsetDateTime.of(latestBatch.snapshotTime(), CST);
                long hoursAgo = Duration.between(dataAsOf, OffsetDateTime.now(CST)).toHours();
                freshness = hoursAgo <= 24 ? TrustedAnswer.FRESH
                    : hoursAgo <= 72 ? TrustedAnswer.DELAYED
                    : TrustedAnswer.STALE;
            } else {
                freshness = TrustedAnswer.UNKNOWN;
            }

            String conclusion = String.format("%s 市场: 领涨板块 %s (%s), 领跌板块 %s (%s)",
                normalizedMarket,
                latestBatch.leaderSectorName() != null ? latestBatch.leaderSectorName() : "未知",
                latestBatch.leaderChangeRate() != null ? latestBatch.leaderChangeRate() + "%" : "N/A",
                latestBatch.laggardSectorName() != null ? latestBatch.laggardSectorName() : "未知",
                latestBatch.laggardChangeRate() != null ? latestBatch.laggardChangeRate() + "%" : "N/A"
            );

            return TrustedAnswer.of(conclusion, dataMap, freshness, dataAsOf,
                List.of(new TrustedAnswer.Evidence("SECTOR_RANKING",
                    String.valueOf(latestBatch.id()),
                    latestBatch.snapshotTime() != null
                        ? OffsetDateTime.of(latestBatch.snapshotTime(), CST)
                        : OffsetDateTime.now(CST))));
        } catch (Exception e) {
            log.error("sectorRankingSummary failed", e);
            return TrustedAnswer.fail("板块排行查询失败");
        }
    }

    /** 8. 单证券行情摘要 — null 安全 */
    public TrustedAnswer securityMarketSummary(String canonicalSymbol) {
        try {
            var snapshots = marketQuoteService.listSnapshots(canonicalSymbol, null, 1, 1);
            var items = snapshots.items();

            if (items == null || items.isEmpty()) {
                return TrustedAnswer.empty(
                    "未找到 " + canonicalSymbol + " 的快照数据（可能尚未采集或代码不正确）",
                    TrustedAnswer.UNKNOWN);
            }

            StockQuoteSnapshotVO latest = items.get(0);
            var secData = new HashMap<String, Object>();
            secData.put("canonicalSymbol", latest.canonicalSymbol());
            secData.put("currentPrice", latest.currentPrice());
            secData.put("openPrice", latest.openPrice());
            secData.put("highPrice", latest.highPrice());
            secData.put("lowPrice", latest.lowPrice());
            secData.put("preClosePrice", latest.preClosePrice());
            secData.put("volume", latest.volume());
            secData.put("amount", latest.amount());
            secData.put("quoteTime", latest.quoteTime() != null ? latest.quoteTime().toString() : null);
            secData.put("dataSource", latest.dataSource());
            secData.put("fetchedAt", latest.fetchedAt() != null ? latest.fetchedAt().toString() : null);
            secData.put("disclaimer", "不构成投资建议");

            // 基于 fetchedAt 计算 freshness
            String freshness;
            OffsetDateTime dataAsOf = null;
            if (latest.fetchedAt() != null) {
                dataAsOf = OffsetDateTime.of(latest.fetchedAt(), CST);
                long hoursAgo = Duration.between(dataAsOf, OffsetDateTime.now(CST)).toHours();
                freshness = hoursAgo <= 1 ? TrustedAnswer.FRESH
                    : hoursAgo <= 6 ? TrustedAnswer.DELAYED
                    : TrustedAnswer.STALE;
            } else {
                freshness = TrustedAnswer.UNKNOWN;
            }

            List<String> warnings = new ArrayList<>();
            warnings.add("不构成投资建议");

            return new TrustedAnswer(
                canonicalSymbol + " 最新行情已加载",
                OffsetDateTime.now(CST),
                dataAsOf,
                freshness,
                List.of(new TrustedAnswer.Evidence("QUOTE_SNAPSHOT",
                    String.valueOf(latest.id()),
                    OffsetDateTime.now(CST))),
                warnings,
                secData
            );
        } catch (Exception e) {
            log.error("securityMarketSummary failed", e);
            return TrustedAnswer.fail("证券行情查询失败");
        }
    }
}
