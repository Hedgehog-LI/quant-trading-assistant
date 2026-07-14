package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.*;
import com.quant.trade.marketdata.dto.CreateSyncPlanDTO;
import com.quant.trade.marketdata.dto.MinuteBarUpsertDTO;
import com.quant.trade.marketdata.dto.UpdateSyncPlanDTO;
import com.quant.trade.marketdata.dto.CreateSyncTaskDTO;
import com.quant.trade.marketdata.manager.MinuteBarQualityManager;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.*;
import com.quant.trade.marketdata.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 行情工作台 + 采集计划 + 分钟 K + 任务明细 + 水位 应用服务。
 * <p>
 * 负责事务编排、DTO→DO 转换、DAO 调用、provider 健康复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataWorkbenchService {

    private final MarketQuoteService marketQuoteService;
    private final StockMinuteBarMapper minuteBarMapper;
    private final MarketTradingSessionMapper tradingSessionMapper;
    private final MarketDataSyncPlanMapper syncPlanMapper;
    private final MarketDataSyncTaskItemMapper taskItemMapper;
    private final MarketDataWatermarkMapper watermarkMapper;
    private final MarketDataAlertMapper alertMapper;
    private final MarketDataSyncTaskMapper syncTaskMapper;
    private final StockBasicMapper stockBasicMapper;
    private final StockDailyBarMapper stockDailyBarMapper;
    private final MinuteBarQualityManager qualityManager;
    private final TradingSessionManager tradingSessionManager;

    private static final int MAX_PAGE_SIZE = 500;

    // ==================== 工作台概览 ====================

    public WorkbenchOverviewVO getOverview() {
        return WorkbenchOverviewVO.builder()
                .providerStatus(marketQuoteService.getProviderStatus())
                .latestSyncAt(getLatestSyncAt())
                .totalSymbols(countSymbols())
                .totalMinuteBars(countMinuteBars())
                .totalDailyBars(countDailyBars())
                .unresolvedHighAlerts(countAlerts("HIGH", false))
                .unresolvedWarnAlerts(countAlerts("WARN", false))
                .failedTasksToday(countFailedTasksToday())
                .recentWatermarks(getRecentWatermarks())
                .recentAlerts(getRecentAlerts())
                .tradingSessions(getTradingSessions())
                .build();
    }

    // ==================== 采集计划 CRUD ====================

    @Transactional
    public MarketDataSyncPlanVO createPlan(CreateSyncPlanDTO dto) {
        validateTaskType(dto.getTaskType());
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .planName(dto.getPlanName())
                .taskType(dto.getTaskType())
                .provider(dto.getProvider())
                .scopeJson(dto.getScopeJson())
                .intervalType(dto.getIntervalType() != null ? dto.getIntervalType() : "")
                .adjustType(dto.getAdjustType() != null ? dto.getAdjustType() : "NONE")
                .triggerType(dto.getTriggerType() != null ? dto.getTriggerType() : WorkbenchConstants.TRIGGER_MANUAL)
                .cronExpr(dto.getCronExpr())
                .includeAuction(Boolean.TRUE.equals(dto.getIncludeAuction()))
                .collectFrequency(dto.getCollectFrequency())
                .enabled(true)
                .description(dto.getDescription())
                .build();
        syncPlanMapper.insert(plan);
        log.info("创建采集计划: id={}, name={}, type={}", plan.getId(), plan.getPlanName(), plan.getTaskType());
        return toPlanVO(plan);
    }

    public PageResultVO<MarketDataSyncPlanVO> listPlans(String taskType, String provider,
                                                         Boolean enabled, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(page, 1) - 1) * safeSize;
        List<MarketDataSyncPlanDO> plans = syncPlanMapper.selectByFilter(taskType, provider, enabled, safeSize, offset);
        long total = syncPlanMapper.countByFilter(taskType, provider, enabled);
        List<MarketDataSyncPlanVO> items = plans.stream().map(this::toPlanVO).collect(Collectors.toList());
        return PageResultVO.of(items, total, page, safeSize);
    }

    public MarketDataSyncPlanVO getPlan(Long id) {
        MarketDataSyncPlanDO plan = syncPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "采集计划不存在: " + id);
        }
        return toPlanVO(plan);
    }

    @Transactional
    public MarketDataSyncPlanVO updatePlan(Long id, UpdateSyncPlanDTO dto) {
        MarketDataSyncPlanDO existing = syncPlanMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "采集计划不存在: " + id);
        }
        existing.setPlanName(dto.getPlanName());
        existing.setScopeJson(dto.getScopeJson());
        if (dto.getIntervalType() != null) existing.setIntervalType(dto.getIntervalType());
        if (dto.getAdjustType() != null) existing.setAdjustType(dto.getAdjustType());
        if (dto.getTriggerType() != null) existing.setTriggerType(dto.getTriggerType());
        existing.setCronExpr(dto.getCronExpr());
        if (dto.getIncludeAuction() != null) existing.setIncludeAuction(dto.getIncludeAuction());
        existing.setCollectFrequency(dto.getCollectFrequency());
        existing.setDescription(dto.getDescription());
        syncPlanMapper.updateById(existing);
        return toPlanVO(existing);
    }

    @Transactional
    public MarketDataSyncPlanVO togglePlan(Long id, boolean enabled) {
        MarketDataSyncPlanDO existing = syncPlanMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "采集计划不存在: " + id);
        }
        syncPlanMapper.updateEnabled(id, enabled, LocalDateTime.now());
        existing.setEnabled(enabled);
        return toPlanVO(existing);
    }

    // ==================== 采集计划手动执行 ====================

    /**
     * 手动执行采集计划：生成 sync_task + task_item，执行写入链路，更新 lastRunAt/lastTaskId。
     * <p>
     * 当前支持 DAILY_BAR_BACKFILL（复用 MarketQuoteService 的日 K 同步链路）。
     * 其他任务类型生成任务记录但标记 SKIPPED（执行链路待后续接入）。
     */
    public MarketDataSyncPlanVO runPlan(Long planId) {
        MarketDataSyncPlanDO plan = syncPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "采集计划不存在: " + planId);
        }

        // 解析 scope 中的 symbols（简单解析 JSON 中的 canonicalSymbol/symbols）
        List<String> symbols = extractSymbolsFromScope(plan.getScopeJson());
        if (symbols.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "采集计划 scope 中未找到有效 symbol: " + plan.getScopeJson());
        }

        // 创建 sync_task
        String idemKey = UUID.nameUUIDFromBytes(
                (plan.getProvider() + plan.getTaskType() + plan.getScopeJson() + "#plan#" + planId + "#" + System.nanoTime())
                        .getBytes()).toString();
        MarketDataSyncTaskDO task = MarketDataSyncTaskDO.builder()
                .taskType(plan.getTaskType())
                .provider(plan.getProvider())
                .scopeJson(plan.getScopeJson())
                .status(MarketDataConstants.TASK_STATUS_RUNNING)
                .idempotencyKey(idemKey)
                .startedAt(LocalDateTime.now())
                .build();
        syncTaskMapper.insert(task);
        Long taskId = task.getId();

        int totalSymbols = symbols.size();
        int successCount = 0;
        int failCount = 0;

        // 逐 symbol 执行
        for (String symbol : symbols) {
            MarketDataSyncTaskItemDO item = MarketDataSyncTaskItemDO.builder()
                    .taskId(taskId).planId(planId).canonicalSymbol(symbol)
                    .scopeDetail(plan.getScopeJson())
                    .status(WorkbenchConstants.ITEM_RUNNING)
                    .rowCount(0).insertedCount(0).updatedCount(0).skippedCount(0)
                    .startedAt(LocalDateTime.now())
                    .build();
            taskItemMapper.insert(item);

            try {
                if ("DAILY_BAR_BACKFILL".equals(plan.getTaskType())) {
                    // 复用 MarketQuoteService 的日 K 同步链路（provider → 幂等写入）
                    CreateSyncTaskDTO dto = new CreateSyncTaskDTO(
                            plan.getTaskType(), plan.getProvider(), symbol,
                            null, null, plan.getAdjustType());
                    marketQuoteService.createAndExecuteDailyBarSync(dto);
                    item.setStatus(WorkbenchConstants.ITEM_SUCCEEDED);
                    item.setInsertedCount(1);
                    item.setRowCount(1);
                    successCount++;
                } else {
                    // 其他任务类型：当前标记 SKIPPED，执行链路待后续接入
                    item.setStatus(WorkbenchConstants.ITEM_SKIPPED);
                    item.setErrorMessage("任务类型 " + plan.getTaskType() + " 的执行链路尚未接入");
                }
            } catch (Exception e) {
                item.setStatus(WorkbenchConstants.ITEM_FAILED);
                item.setErrorCode(ErrorCodeEnum.INTERNAL_ERROR.getCode());
                item.setErrorMessage(e.getMessage() != null && e.getMessage().length() > 480
                        ? e.getMessage().substring(0, 480) : e.getMessage());
                failCount++;
                log.warn("采集计划 {} symbol {} 执行失败: {}", planId, symbol, e.getMessage());
            }
            item.setFinishedAt(LocalDateTime.now());
            taskItemMapper.updateById(item);
        }

        // 更新 sync_task 状态
        task.setStatus(failCount > 0 && successCount > 0
                ? MarketDataConstants.TASK_STATUS_PARTIAL_FAILED
                : failCount > 0 ? MarketDataConstants.TASK_STATUS_FAILED
                : MarketDataConstants.TASK_STATUS_SUCCEEDED);
        task.setTotalCount(totalSymbols);
        task.setSuccessCount(successCount);
        task.setFailCount(failCount);
        task.setFinishedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);

        // 更新 plan 的 lastRunAt / lastTaskId
        syncPlanMapper.updateLastRun(planId, taskId, LocalDateTime.now());

        log.info("采集计划 {} 手动执行完成: taskId={}, symbols={}, success={}, fail={}",
                planId, taskId, totalSymbols, successCount, failCount);
        return toPlanVO(syncPlanMapper.selectById(planId));
    }

    /** 从 scope JSON 中提取 symbol 列表（支持 {"canonicalSymbol":"SH.600519"} 和 {"symbols":["SH.600519"]} 两种格式）。 */
    private List<String> extractSymbolsFromScope(String scopeJson) {
        List<String> symbols = new ArrayList<>();
        if (scopeJson == null || scopeJson.isBlank()) return symbols;
        // 简单正则提取，避免引入 JSON 解析依赖（scope 格式固定）
        java.util.regex.Matcher single = java.util.regex.Pattern
                .compile("\"canonicalSymbol\"\\s*:\\s*\"([^\"]+)\"").matcher(scopeJson);
        if (single.find()) {
            symbols.add(single.group(1));
            return symbols;
        }
        java.util.regex.Matcher multi = java.util.regex.Pattern
                .compile("\"symbols\"\\s*:\\s*\\[([^\\]]+)\\]").matcher(scopeJson);
        if (multi.find()) {
            String arr = multi.group(1);
            java.util.regex.Matcher sm = java.util.regex.Pattern
                    .compile("\"([^\"]+)\"").matcher(arr);
            while (sm.find()) {
                symbols.add(sm.group(1));
            }
        }
        return symbols;
    }

    // ==================== 任务明细 ====================

    public PageResultVO<MarketDataSyncTaskItemVO> listTaskItems(Long taskId, String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(page, 1) - 1) * safeSize;
        List<MarketDataSyncTaskItemDO> items = taskItemMapper.selectByTaskId(taskId, status, safeSize, offset);
        long total = taskItemMapper.countByTaskId(taskId, status);
        List<MarketDataSyncTaskItemVO> vos = items.stream().map(this::toTaskItemVO).collect(Collectors.toList());
        return PageResultVO.of(vos, total, page, safeSize);
    }

    // ==================== 分钟 K 查询 ====================

    public PageResultVO<StockMinuteBarVO> listMinuteBars(String canonicalSymbol, String intervalType,
                                                          String adjustType, String dataSource,
                                                          LocalDateTime fromTime, LocalDateTime toTime,
                                                          String tradeDate, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(page, 1) - 1) * safeSize;
        List<StockMinuteBarDO> bars = minuteBarMapper.selectByFilter(canonicalSymbol, intervalType, adjustType,
                dataSource, fromTime, toTime, tradeDate, safeSize, offset);
        long total = minuteBarMapper.countByFilter(canonicalSymbol, intervalType, adjustType,
                dataSource, fromTime, toTime, tradeDate);
        List<StockMinuteBarVO> vos = bars.stream().map(this::toMinuteBarVO).collect(Collectors.toList());
        return PageResultVO.of(vos, total, page, safeSize);
    }

    // ==================== 分钟 K 写入（带质量校验 + 幂等 + 水位）====================

    /**
     * 写入单条分钟 K。幂等键冲突时：
     * - 内容一致 → skipped
     * - 内容冲突 → 产生 alert，不覆盖，返回 SUSPECT
     * 质量校验失败 → 标记 REJECTED 但不写库，产生 alert。
     */
    @Transactional
    public MinuteBarUpsertResult upsertMinuteBar(MinuteBarUpsertDTO dto) {
        StockMinuteBarDO bar = toMinuteBarDO(dto);
        String quality = qualityManager.validate(bar);
        bar.setQualityStatus(quality);

        if (WorkbenchConstants.QUALITY_REJECTED.equals(quality)) {
            createQualityAlert(bar, "分钟K质量校验失败(REJECTED)");
            return MinuteBarUpsertResult.rejected();
        }

        // 交易日 + 交易时段校验
        String marketCode = extractMarketCode(bar.getCanonicalSymbol());
        boolean isTradingDay = tradingSessionManager.isTradingDay(marketCode, bar.getTradeDate());
        if (!isTradingDay) {
            bar.setQualityStatus(WorkbenchConstants.QUALITY_REJECTED);
            createQualityAlert(bar, "分钟K落库失败: 非交易日 " + bar.getTradeDate());
            return MinuteBarUpsertResult.rejected();
        }
        List<int[]> sessionWindows = tradingSessionManager.getSessionWindows(marketCode, false);
        if (!qualityManager.isBarInSession(bar.getBarStartTime(), sessionWindows, false)) {
            bar.setQualityStatus(WorkbenchConstants.QUALITY_SUSPECT);
            createQualityAlert(bar, "分钟K时段校验: bar 时间不在允许交易时段内，标记 SUSPECT");
            // SUSPECT 仍可落库但标记，不阻断
        }

        StockMinuteBarDO existing = minuteBarMapper.selectByUniqueKey(bar.getCanonicalSymbol(),
                bar.getBarStartTime(), bar.getIntervalType(), bar.getAdjustType(), bar.getDataSource());
        if (existing != null) {
            if (qualityManager.isContentConflict(existing, bar)) {
                // 冲突：产生 alert，不覆盖
                createQualityAlert(bar, "分钟K幂等键内容冲突，已保留旧数据不覆盖");
                return MinuteBarUpsertResult.conflict();
            }
            return MinuteBarUpsertResult.skipped();
        }

        minuteBarMapper.insert(bar);
        updateWatermark(bar);
        return new MinuteBarUpsertResult("INSERTED", bar.getQualityStatus());
    }

    /** 从 canonical symbol 提取市场代码（SH.600519 → CN_A）。 */
    private String extractMarketCode(String canonicalSymbol) {
        if (canonicalSymbol == null) return WorkbenchConstants.MARKET_CN_A;
        if (canonicalSymbol.startsWith("SH.") || canonicalSymbol.startsWith("SZ.") || canonicalSymbol.startsWith("BJ.")) {
            return WorkbenchConstants.MARKET_CN_A;
        }
        if (canonicalSymbol.startsWith("HK.")) return "HK";
        if (canonicalSymbol.startsWith("US.")) return "US";
        return WorkbenchConstants.MARKET_CN_A;
    }

    // ==================== 交易时段 / 日历 ====================

    public List<MarketTradingSessionVO> getTradingSessions() {
        List<MarketTradingSessionDO> sessions = tradingSessionMapper.selectByMarket(
                WorkbenchConstants.MARKET_CN_A, true);
        if (sessions == null || sessions.isEmpty()) {
            // 安全回退：返回内存默认窗口（不写 DB，避免 GET 请求里的懒初始化死锁）
            return List.of(
                    toSessionVO(MarketTradingSessionDO.builder().id(0L).marketCode("CN_A")
                            .sessionType("AUCTION").sessionName("集合竞价（开盘）")
                            .startTime("09:15").endTime("09:25").isAuction(true).sortOrder(1).enabled(true).build()),
                    toSessionVO(MarketTradingSessionDO.builder().id(0L).marketCode("CN_A")
                            .sessionType("AM").sessionName("上午连续竞价")
                            .startTime("09:30").endTime("11:30").isAuction(false).sortOrder(2).enabled(true).build()),
                    toSessionVO(MarketTradingSessionDO.builder().id(0L).marketCode("CN_A")
                            .sessionType("PM").sessionName("下午连续竞价")
                            .startTime("13:00").endTime("14:57").isAuction(false).sortOrder(3).enabled(true).build()),
                    toSessionVO(MarketTradingSessionDO.builder().id(0L).marketCode("CN_A")
                            .sessionType("AUCTION").sessionName("集合竞价（收盘）")
                            .startTime("14:57").endTime("15:00").isAuction(true).sortOrder(4).enabled(true).build())
            );
        }
        return sessions.stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    /** 应用启动后幂等初始化 A 股默认交易时段（不在 GET 请求路径里做，避免并发死锁）。 */
    @jakarta.annotation.PostConstruct
    public void initSessionsOnStartup() {
        try {
            tradingSessionManager.initDefaultCnASessions();
        } catch (Exception e) {
            log.warn("启动初始化交易时段失败（非致命，将使用内存默认回退）: {}", e.getMessage());
        }
    }

    public boolean isTradingDay(String marketCode, LocalDate date) {
        return tradingSessionManager.isTradingDay(marketCode, date);
    }

    // ==================== 水位 ====================

    public PageResultVO<MarketDataWatermarkVO> listWatermarks(String canonicalSymbol, String dataSource,
                                                               String intervalType, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(page, 1) - 1) * safeSize;
        List<MarketDataWatermarkDO> wms = watermarkMapper.selectByFilter(canonicalSymbol, dataSource,
                intervalType, safeSize, offset);
        long total = watermarkMapper.countByFilter(canonicalSymbol, dataSource, intervalType);
        List<MarketDataWatermarkVO> vos = wms.stream().map(this::toWatermarkVO).collect(Collectors.toList());
        return PageResultVO.of(vos, total, page, safeSize);
    }

    // ==================== 内部方法 ====================

    private void updateWatermark(StockMinuteBarDO bar) {
        MarketDataWatermarkDO existing = watermarkMapper.selectByUniqueKey(bar.getCanonicalSymbol(),
                bar.getDataSource(), bar.getIntervalType(), bar.getAdjustType());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            watermarkMapper.insert(MarketDataWatermarkDO.builder()
                    .canonicalSymbol(bar.getCanonicalSymbol())
                    .dataSource(bar.getDataSource())
                    .intervalType(bar.getIntervalType())
                    .adjustType(bar.getAdjustType())
                    .lastSuccessTime(now)
                    .lastTradeDate(bar.getTradeDate())
                    .lastBarTime(bar.getBarStartTime())
                    .totalRows(1L)
                    .build());
        } else {
            existing.setLastSuccessTime(now);
            existing.setLastTradeDate(bar.getTradeDate());
            existing.setLastBarTime(bar.getBarStartTime());
            existing.setTotalRows(existing.getTotalRows() + 1);
            watermarkMapper.updateByUniqueKey(existing);
        }
    }

    private void createQualityAlert(StockMinuteBarDO bar, String message) {
        alertMapper.insert(MarketDataAlertDO.builder()
                .alertType(WorkbenchConstants.ALERT_QUALITY_CONFLICT)
                .severity("WARN")
                .canonicalSymbol(bar.getCanonicalSymbol())
                .provider(bar.getDataSource())
                .message(message + " symbol=" + bar.getCanonicalSymbol()
                        + " time=" + bar.getBarStartTime() + " interval=" + bar.getIntervalType())
                .resolved(false)
                .build());
    }

    private StockMinuteBarDO toMinuteBarDO(MinuteBarUpsertDTO dto) {
        LocalDateTime barStart = dto.getBarStartTime();
        return StockMinuteBarDO.builder()
                .canonicalSymbol(dto.getCanonicalSymbol())
                .tradeDate(barStart.toLocalDate())
                .barStartTime(barStart)
                .barEndTime(dto.getBarEndTime())
                .intervalType(dto.getIntervalType())
                .sessionType(dto.getSessionType() != null ? dto.getSessionType() : WorkbenchConstants.SESSION_REGULAR)
                .openPrice(dto.getOpenPrice())
                .highPrice(dto.getHighPrice())
                .lowPrice(dto.getLowPrice())
                .closePrice(dto.getClosePrice())
                .volume(dto.getVolume())
                .amount(dto.getAmount())
                .turnoverRate(dto.getTurnoverRate())
                .adjustType(dto.getAdjustType())
                .dataSource(dto.getDataSource())
                .fetchedAt(LocalDateTime.now())
                .qualityStatus(WorkbenchConstants.QUALITY_VALID)
                .build();
    }

    private void validateTaskType(String taskType) {
        if (taskType == null || taskType.isBlank()) return;
        List<String> valid = List.of("SECURITY_MASTER_SYNC", "DAILY_BAR_BACKFILL", "MINUTE_BAR_BACKFILL",
                "INTRADAY_QUOTE_REFRESH", "INTRADAY_MINUTE_REFRESH", "CALENDAR_SYNC",
                "SEGMENT_SYNC", "QUALITY_AUDIT");
        if (!valid.contains(taskType)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "不支持的任务类型: " + taskType);
        }
    }

    // VO 转换
    private StockMinuteBarVO toMinuteBarVO(StockMinuteBarDO d) {
        return StockMinuteBarVO.builder()
                .id(d.getId()).canonicalSymbol(d.getCanonicalSymbol()).tradeDate(d.getTradeDate())
                .barStartTime(d.getBarStartTime()).barEndTime(d.getBarEndTime())
                .intervalType(d.getIntervalType()).sessionType(d.getSessionType())
                .openPrice(d.getOpenPrice()).highPrice(d.getHighPrice()).lowPrice(d.getLowPrice()).closePrice(d.getClosePrice())
                .volume(d.getVolume()).amount(d.getAmount()).turnoverRate(d.getTurnoverRate())
                .adjustType(d.getAdjustType()).dataSource(d.getDataSource())
                .qualityStatus(d.getQualityStatus()).fetchedAt(d.getFetchedAt())
                .build();
    }

    private MarketTradingSessionVO toSessionVO(MarketTradingSessionDO d) {
        return MarketTradingSessionVO.builder()
                .id(d.getId()).marketCode(d.getMarketCode()).sessionType(d.getSessionType())
                .sessionName(d.getSessionName()).startTime(d.getStartTime()).endTime(d.getEndTime())
                .isAuction(d.getIsAuction()).sortOrder(d.getSortOrder()).enabled(d.getEnabled())
                .build();
    }

    private MarketDataSyncPlanVO toPlanVO(MarketDataSyncPlanDO d) {
        return MarketDataSyncPlanVO.builder()
                .id(d.getId()).planName(d.getPlanName()).taskType(d.getTaskType()).provider(d.getProvider())
                .scopeJson(d.getScopeJson()).intervalType(d.getIntervalType()).adjustType(d.getAdjustType())
                .triggerType(d.getTriggerType()).cronExpr(d.getCronExpr()).includeAuction(d.getIncludeAuction())
                .collectFrequency(d.getCollectFrequency()).enabled(d.getEnabled()).description(d.getDescription())
                .lastRunAt(d.getLastRunAt()).lastTaskId(d.getLastTaskId())
                .createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt())
                .build();
    }

    private MarketDataSyncTaskItemVO toTaskItemVO(MarketDataSyncTaskItemDO d) {
        return MarketDataSyncTaskItemVO.builder()
                .id(d.getId()).taskId(d.getTaskId()).planId(d.getPlanId()).canonicalSymbol(d.getCanonicalSymbol())
                .scopeDetail(d.getScopeDetail()).status(d.getStatus()).rowCount(d.getRowCount())
                .insertedCount(d.getInsertedCount()).updatedCount(d.getUpdatedCount()).skippedCount(d.getSkippedCount())
                .errorCode(d.getErrorCode()).errorMessage(d.getErrorMessage())
                .startedAt(d.getStartedAt()).finishedAt(d.getFinishedAt()).createdAt(d.getCreatedAt())
                .build();
    }

    private MarketDataWatermarkVO toWatermarkVO(MarketDataWatermarkDO d) {
        return MarketDataWatermarkVO.builder()
                .id(d.getId()).canonicalSymbol(d.getCanonicalSymbol()).dataSource(d.getDataSource())
                .intervalType(d.getIntervalType()).adjustType(d.getAdjustType())
                .lastSuccessTime(d.getLastSuccessTime()).lastTradeDate(d.getLastTradeDate())
                .lastBarTime(d.getLastBarTime()).totalRows(d.getTotalRows()).updatedAt(d.getUpdatedAt())
                .build();
    }

    // ==================== 概览聚合（接 DAO 真实查询）====================

    private LocalDateTime getLatestSyncAt() {
        // 取最近成功的水位时间
        List<MarketDataWatermarkDO> recent = watermarkMapper.selectByFilter(null, null, null, 1, 0);
        if (recent != null && !recent.isEmpty() && recent.get(0).getLastSuccessTime() != null) {
            return recent.get(0).getLastSuccessTime();
        }
        return null;
    }

    private long countSymbols() {
        return stockBasicMapper.countByFilter(null, null);
    }

    private long countMinuteBars() {
        return minuteBarMapper.countByFilter(null, null, null, null, null, null, null);
    }

    private long countDailyBars() {
        return stockDailyBarMapper.countByFilter(null, null, null, null, null);
    }

    private long countAlerts(String severity, boolean resolved) {
        return alertMapper.countByFilter(resolved, severity, null);
    }

    private long countFailedTasksToday() {
        return syncTaskMapper.countByFilter("FAILED", null);
    }

    private List<MarketDataWatermarkVO> getRecentWatermarks() {
        List<MarketDataWatermarkDO> wms = watermarkMapper.selectByFilter(null, null, null, 5, 0);
        if (wms == null) return new ArrayList<>();
        return wms.stream().map(this::toWatermarkVO).collect(Collectors.toList());
    }

    private List<MarketDataAlertVO> getRecentAlerts() {
        List<MarketDataAlertDO> alerts = alertMapper.selectByFilter(false, null, null, 5, 0);
        if (alerts == null) return new ArrayList<>();
        return alerts.stream().map(this::toAlertVO).collect(Collectors.toList());
    }

    private MarketDataAlertVO toAlertVO(MarketDataAlertDO d) {
        return new MarketDataAlertVO(
                d.getId(), d.getAlertType(), d.getSeverity(),
                d.getCanonicalSymbol(), d.getQuoteTime(), d.getTradeDate(),
                d.getProvider(), d.getTaskId(), d.getMessage(),
                d.getTriggerValueJson(), d.getResolved(), d.getCreatedAt());
    }

    /** 分钟 K 写入结果。 */
    public record MinuteBarUpsertResult(String result, String qualityStatus) {
        public static MinuteBarUpsertResult inserted() { return new MinuteBarUpsertResult("INSERTED", "VALID"); }
        public static MinuteBarUpsertResult skipped() { return new MinuteBarUpsertResult("SKIPPED", "VALID"); }
        public static MinuteBarUpsertResult conflict() { return new MinuteBarUpsertResult("CONFLICT", "SUSPECT"); }
        public static MinuteBarUpsertResult rejected() { return new MinuteBarUpsertResult("REJECTED", "REJECTED"); }
    }
}
