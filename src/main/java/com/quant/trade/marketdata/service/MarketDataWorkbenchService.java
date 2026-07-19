package com.quant.trade.marketdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.quant.trade.marketdata.manager.SyncPlanValidationManager;
import com.quant.trade.marketdata.model.*;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import com.quant.trade.marketdata.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private final ObjectMapper objectMapper;
    private final TaskReconcileService taskReconcileService;
    private final SyncPlanValidationManager syncPlanValidationManager;
    private final MarketDataPlanExecutionService planExecutionService;

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
        syncPlanValidationManager.validate(plan);
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
        if (dto.getTaskType() != null) existing.setTaskType(dto.getTaskType());
        if (dto.getProvider() != null) existing.setProvider(dto.getProvider());
        existing.setPlanName(dto.getPlanName());
        existing.setScopeJson(dto.getScopeJson());
        if (dto.getIntervalType() != null) existing.setIntervalType(dto.getIntervalType());
        if (dto.getAdjustType() != null) existing.setAdjustType(dto.getAdjustType());
        if (dto.getTriggerType() != null) existing.setTriggerType(dto.getTriggerType());
        existing.setCronExpr(dto.getCronExpr());
        if (dto.getIncludeAuction() != null) existing.setIncludeAuction(dto.getIncludeAuction());
        existing.setCollectFrequency(dto.getCollectFrequency());
        existing.setDescription(dto.getDescription());
        validateTaskType(existing.getTaskType());
        syncPlanValidationManager.validate(existing);
        syncPlanMapper.updateById(existing);
        return toPlanVO(existing);
    }

    @Transactional
    public MarketDataSyncPlanVO togglePlan(Long id, boolean enabled) {
        MarketDataSyncPlanDO existing = syncPlanMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "采集计划不存在: " + id);
        }
        if (enabled) syncPlanValidationManager.validate(existing);
        syncPlanMapper.updateEnabled(id, enabled, LocalDateTime.now());
        existing.setEnabled(enabled);
        return toPlanVO(existing);
    }

    // ==================== 采集计划手动执行 ====================

    /**
     * 手动执行采集计划：生成 sync_task + task_item，执行写入链路，更新 lastRunAt/lastTaskId。
     * <p>
     * 当前仅支持 DAILY_BAR_BACKFILL（复用 MarketQuoteService 的日 K 同步链路）。
     * 其他任务类型直接抛 BusinessException，不创建空壳任务误导用户。
     * <p>
     * 状态映射（严格）：
     * <ul>
     *   <li>子任务 SUCCEEDED → item SUCCEEDED</li>
     *   <li>子任务 PARTIAL_FAILED → item PARTIAL_FAILED（不是成功）</li>
     *   <li>子任务 FAILED → item FAILED</li>
     *   <li>子任务 PENDING/RUNNING → item 保留对应非终态；主任务不写 SUCCEEDED/finishedAt</li>
     *   <li>子任务未知/null → item FAILED（可解释失败）</li>
     * </ul>
     * 计数口径：task 的 total/success/fail/inserted/updated/skipped 全部使用"行情数据行"单位，
     * 从子任务返回值累加；symbol 维度状态由 task_item 表达，不混入 count 字段。
     * 主子任务追踪：item 保存 sub_task_id。
     */
    public MarketDataSyncPlanVO runPlan(Long planId) {
        MarketDataSyncPlanDO plan = syncPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "采集计划不存在: " + planId);
        }

        var validation = syncPlanValidationManager.validate(plan);
        if (WorkbenchConstants.TASK_MINUTE_BAR_BACKFILL.equals(plan.getTaskType())) {
            planExecutionService.executeMinutePlan(plan, LocalDateTime.now());
            return toPlanVO(syncPlanMapper.selectById(planId));
        }

        // 盘中刷新只允许 scheduler 触发；页面不把它伪装成手工补档。
        if (!WorkbenchConstants.TASK_DAILY_BAR_BACKFILL.equals(plan.getTaskType())) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "任务类型 " + plan.getTaskType() + " 不支持立即执行；盘中刷新由交易时段 scheduler 触发");
        }

        // 用 Jackson 解析结构化 scope（含校验）
        PlanScope scope = parseScope(plan.getScopeJson());

        // 创建 sync_task（主任务）
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

        // symbol 维度状态计数（仅用于判定主任务状态，不写入 task count 字段）
        int symbolsSucceeded = 0;
        int symbolsFailed = 0;
        int symbolsPartialFailed = 0;
        int symbolsNonTerminal = 0;

        // 行维度计数（写入 task count 字段，全部使用行情数据行单位，逐项从子任务返回值累加）
        int totalRows = 0;
        int totalSuccess = 0;
        int totalFail = 0;
        int totalInserted = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;

        // 逐 symbol 执行 DAILY_BAR_BACKFILL，用子任务返回值汇总
        for (String symbol : scope.symbols) {
            MarketDataSyncTaskItemDO item = MarketDataSyncTaskItemDO.builder()
                    .taskId(taskId).planId(planId).canonicalSymbol(symbol)
                    .scopeDetail(plan.getScopeJson())
                    .status(WorkbenchConstants.ITEM_RUNNING)
                    .rowCount(0).insertedCount(0).updatedCount(0).skippedCount(0)
                    .startedAt(LocalDateTime.now())
                    .build();
            taskItemMapper.insert(item);

            try {
                CreateSyncTaskDTO dto = new CreateSyncTaskDTO(
                        plan.getTaskType(), plan.getProvider(), symbol,
                        scope.startDate, scope.endDate, plan.getAdjustType());
                MarketDataSyncTaskVO subTask = marketQuoteService.createAndExecuteDailyBarSync(dto);

                // 保存子任务 ID 到 item（主子任务追踪）
                item.setSubTaskId(subTask.id());

                // 从子任务返回值逐项累加行维度计数（空值按 0）
                int subInserted = subTask.insertedCount() != null ? subTask.insertedCount() : 0;
                int subUpdated = subTask.updatedCount() != null ? subTask.updatedCount() : 0;
                int subSkipped = subTask.skippedCount() != null ? subTask.skippedCount() : 0;
                int subTotal = subTask.totalCount() != null ? subTask.totalCount() : 0;
                int subSuccess = subTask.successCount() != null ? subTask.successCount() : 0;
                int subFail = subTask.failCount() != null ? subTask.failCount() : 0;

                item.setInsertedCount(subInserted);
                item.setUpdatedCount(subUpdated);
                item.setSkippedCount(subSkipped);
                item.setRowCount(subTotal);
                totalInserted += subInserted;
                totalUpdated += subUpdated;
                totalSkipped += subSkipped;
                totalRows += subTotal;
                totalSuccess += subSuccess;
                totalFail += subFail;

                // 严格状态映射
                String subStatus = subTask.status();
                if (MarketDataConstants.TASK_STATUS_SUCCEEDED.equals(subStatus)) {
                    item.setStatus(WorkbenchConstants.ITEM_SUCCEEDED);
                    symbolsSucceeded++;
                } else if (MarketDataConstants.TASK_STATUS_PARTIAL_FAILED.equals(subStatus)) {
                    item.setStatus(WorkbenchConstants.ITEM_PARTIAL_FAILED);
                    item.setErrorMessage("子任务部分失败");
                    symbolsPartialFailed++;
                } else if (MarketDataConstants.TASK_STATUS_PENDING.equals(subStatus)) {
                    item.setStatus(WorkbenchConstants.ITEM_PENDING);
                    item.setErrorMessage("子任务仍在排队中（幂等复用）");
                    symbolsNonTerminal++;
                } else if (MarketDataConstants.TASK_STATUS_RUNNING.equals(subStatus)) {
                    item.setStatus(WorkbenchConstants.ITEM_RUNNING);
                    item.setErrorMessage("子任务仍在运行中（幂等复用）");
                    symbolsNonTerminal++;
                } else {
                    // FAILED 或未知/null 状态 → 可解释失败
                    item.setStatus(WorkbenchConstants.ITEM_FAILED);
                    item.setErrorCode(subTask.lastErrorCode() != null ? subTask.lastErrorCode()
                            : ErrorCodeEnum.INTERNAL_ERROR.getCode());
                    item.setErrorMessage("子任务状态异常: " + (subStatus != null ? subStatus : "null"));
                    symbolsFailed++;
                }
            } catch (BusinessException e) {
                // 业务异常保留原错误码，不降级成 INTERNAL_ERROR
                item.setStatus(WorkbenchConstants.ITEM_FAILED);
                item.setErrorCode(e.getErrorCode().getCode());
                item.setErrorMessage(e.getMessage() != null && e.getMessage().length() > 480
                        ? e.getMessage().substring(0, 480) : e.getMessage());
                symbolsFailed++;
                log.warn("采集计划 {} symbol {} 执行业务失败: {} {}", planId, symbol, e.getErrorCode().getCode(), e.getMessage());
            } catch (Exception e) {
                item.setStatus(WorkbenchConstants.ITEM_FAILED);
                item.setErrorCode(ErrorCodeEnum.INTERNAL_ERROR.getCode());
                item.setErrorMessage(e.getMessage() != null && e.getMessage().length() > 480
                        ? e.getMessage().substring(0, 480) : e.getMessage());
                symbolsFailed++;
                log.warn("采集计划 {} symbol {} 执行系统异常: {}", planId, symbol, e.getMessage());
            }

            // 非终态 item 不设 finishedAt（保留 RUNNING/PENDING 可追踪）
            if (!WorkbenchConstants.ITEM_PENDING.equals(item.getStatus())
                    && !WorkbenchConstants.ITEM_RUNNING.equals(item.getStatus())) {
                item.setFinishedAt(LocalDateTime.now());
            }
            taskItemMapper.updateById(item);
        }

        // 主任务状态：有非终态子任务 → RUNNING；否则按成功/部分失败/全失败判定
        String mainStatus;
        if (symbolsNonTerminal > 0) {
            mainStatus = MarketDataConstants.TASK_STATUS_RUNNING;
        } else if (symbolsFailed > 0 || symbolsPartialFailed > 0) {
            mainStatus = (symbolsSucceeded > 0 || symbolsPartialFailed > 0)
                    ? MarketDataConstants.TASK_STATUS_PARTIAL_FAILED
                    : MarketDataConstants.TASK_STATUS_FAILED;
        } else {
            mainStatus = MarketDataConstants.TASK_STATUS_SUCCEEDED;
        }

        task.setStatus(mainStatus);
        // count 字段全部使用行情数据行单位，逐项从子任务返回值累加，不反推
        task.setTotalCount(totalRows);
        task.setSuccessCount(totalSuccess);
        task.setFailCount(totalFail);
        task.setInsertedCount(totalInserted);
        task.setUpdatedCount(totalUpdated);
        task.setSkippedCount(totalSkipped);
        // 非终态主任务不设 finishedAt
        if (!MarketDataConstants.TASK_STATUS_RUNNING.equals(mainStatus)) {
            task.setFinishedAt(LocalDateTime.now());
        }
        syncTaskMapper.updateById(task);

        // 更新 plan 的 lastRunAt / lastTaskId
        syncPlanMapper.updateLastRun(planId, taskId, LocalDateTime.now());

        log.info("采集计划 {} 手动执行完成: taskId={}, status={}, symbols(succ/partial/fail/nonTerm)={}/{}/{}/{}, rows(total/succ/fail/ins/upd/skip)={}/{}/{}/{}/{}/{}",
                planId, taskId, mainStatus, symbolsSucceeded, symbolsPartialFailed, symbolsFailed, symbolsNonTerminal,
                totalRows, totalSuccess, totalFail, totalInserted, totalUpdated, totalSkipped);
        return toPlanVO(syncPlanMapper.selectById(planId));
    }

    /**
     * 收敛非终态任务。委托到独立 {@link TaskReconcileService}（Spring 代理），确保 {@code @Transactional} 生效。
     */
    public MarketDataSyncTaskVO reconcileTask(Long taskId) {
        return taskReconcileService.reconcileTask(taskId);
    }

    /**
     * 用 Jackson 解析 scope JSON，提取 symbols + startDate + endDate。
     * <p>
     * 校验规则：
     * - symbols 从 canonicalSymbol 或 symbols 数组提取，去空白、去重。
     * - 每个 symbol 按统一 canonical 规则规范化并校验。
     * - startDate <= endDate（若都存在）。
     * - 非法 JSON、非法日期、空 symbol 或日期范围非法抛 BusinessException。
     */
    private PlanScope parseScope(String scopeJson) {
        PlanScope scope = new PlanScope();
        if (scopeJson == null || scopeJson.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "scope 不能为空");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(scopeJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "scope JSON 格式不合法: " + e.getMessage());
        }

        // 提取 symbols（去空白、去重）
        LinkedHashSet<String> symbolSet = new LinkedHashSet<>();
        if (root.has("canonicalSymbol") && !root.get("canonicalSymbol").isNull()) {
            String s = root.get("canonicalSymbol").asText().trim();
            if (!s.isEmpty()) symbolSet.add(s);
        }
        if (root.has("symbols") && root.get("symbols").isArray()) {
            for (JsonNode sn : root.get("symbols")) {
                String s = sn.asText().trim();
                if (!s.isEmpty()) symbolSet.add(s);
            }
        }

        // 规范化并校验每个 symbol
        LinkedHashSet<String> normalizedSymbols = new LinkedHashSet<>();
        for (String symbol : symbolSet) {
            try {
                normalizedSymbols.add(CanonicalSymbolUtils.normalize(symbol));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL,
                        "scope 中 symbol 格式不合法: " + symbol + "，" + exception.getMessage());
            }
        }
        scope.symbols.addAll(normalizedSymbols);

        if (scope.symbols.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "scope 中未找到有效 symbol: " + scopeJson);
        }

        // 解析并校验日期
        try {
            if (root.has("startDate") && !root.get("startDate").isNull()) {
                scope.startDate = LocalDate.parse(root.get("startDate").asText());
            }
            if (root.has("endDate") && !root.get("endDate").isNull()) {
                scope.endDate = LocalDate.parse(root.get("endDate").asText());
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "scope 中日期格式不合法（需要 YYYY-MM-DD）: " + e.getMessage());
        }
        if (scope.startDate != null && scope.endDate != null && scope.startDate.isAfter(scope.endDate)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "scope 中 startDate 不能晚于 endDate");
        }

        return scope;
    }

    /** scope 解析中间结构。 */
    private static class PlanScope {
        final List<String> symbols = new ArrayList<>();
        LocalDate startDate;
        LocalDate endDate;
    }

    // ==================== 任务明细 ====================

    public PageResultVO<MarketDataSyncTaskItemVO> listTaskItems(Long taskId, String status, int page, int size) {
        // 懒收敛：如果父任务 RUNNING，通过独立 TaskReconcileService（Spring 代理）触发事务收敛
        MarketDataSyncTaskDO task = syncTaskMapper.selectById(taskId);
        if (task != null && MarketDataConstants.TASK_STATUS_RUNNING.equals(task.getStatus())) {
            try {
                taskReconcileService.reconcileTask(taskId);
            } catch (Exception e) {
                log.warn("懒收敛任务 {} 失败（降级返回旧数据）: {}", taskId, e.getMessage());
            }
        }
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
        var validation = syncPlanValidationManager.inspect(d);
        return MarketDataSyncPlanVO.builder()
                .id(d.getId()).planName(d.getPlanName()).taskType(d.getTaskType()).provider(d.getProvider())
                .scopeJson(d.getScopeJson()).intervalType(d.getIntervalType()).adjustType(d.getAdjustType())
                .triggerType(d.getTriggerType()).cronExpr(d.getCronExpr()).includeAuction(d.getIncludeAuction())
                .collectFrequency(d.getCollectFrequency()).enabled(d.getEnabled()).description(d.getDescription())
                .lastRunAt(d.getLastRunAt()).lastTaskId(d.getLastTaskId())
                .configurationStatus(validation.errors().isEmpty() ? "VALID" : "NEEDS_ATTENTION")
                .validationErrors(validation.errors())
                .manuallyRunnable(validation.manuallyRunnable())
                .automaticallyRunnable(validation.automaticallyRunnable())
                .createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt())
                .build();
    }

    private MarketDataSyncTaskItemVO toTaskItemVO(MarketDataSyncTaskItemDO d) {
        return MarketDataSyncTaskItemVO.builder()
                .id(d.getId()).taskId(d.getTaskId()).planId(d.getPlanId()).subTaskId(d.getSubTaskId()).canonicalSymbol(d.getCanonicalSymbol())
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
