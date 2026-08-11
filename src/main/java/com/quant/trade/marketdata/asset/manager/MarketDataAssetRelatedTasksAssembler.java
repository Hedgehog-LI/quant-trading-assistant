package com.quant.trade.marketdata.asset.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetRelatedTasksVO;
import com.quant.trade.marketdata.dao.MarketDataSyncPlanMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskItemMapper;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import com.quant.trade.marketdata.model.MarketDataSyncTaskItemDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.util.MarketDataAssetTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P1.9-A related-tasks 组装：与该证券相关的采集计划 + 最近采集记录（分页）。
 * <p>
 * 血缘口径：scope_json 包含证券 + interval 匹配 + 时间范围过滤，不声称逐条 K 线血缘。
 */
@Component
@RequiredArgsConstructor
public class MarketDataAssetRelatedTasksAssembler {

    private static final int PLAN_FETCH_LIMIT = 500;
    private static final int RUN_FETCH_LIMIT = 500;
    private static final int MAX_PAGE_SIZE = 100;

    private final MarketDataSyncPlanMapper syncPlanMapper;
    private final MarketDataSyncTaskItemMapper taskItemMapper;
    private final ObjectMapper objectMapper;
    private final MarketDataAssetSecurityMeta securityMeta;
    private final MarketDataAssetSeriesQueryParser queryParser;

    public MarketDataAssetRelatedTasksVO build(String rawSymbol, String interval, String from, String to,
                                               int page, int size) {
        String canonicalSymbol = securityMeta.normalize(rawSymbol);
        StockBasicDO security = securityMeta.loadSecurity(canonicalSymbol);
        LocalDate fromDate = queryParser.parseOptionalDate(from, "from");
        LocalDate toDate = queryParser.parseOptionalDate(to, "to");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "from 不能晚于 to");
        }

        Map<Long, MarketDataSyncPlanDO> planById = loadPlansById();
        List<MarketDataAssetRelatedTasksVO.RelatedTaskItem> plans = new ArrayList<>();
        for (MarketDataSyncPlanDO plan : planById.values()) {
            if (!scopeContainsSymbol(plan.getScopeJson(), canonicalSymbol)) {
                continue;
            }
            if (!matchesInterval(plan.getIntervalType(), interval)) {
                continue;
            }
            if (!planOverlapsRange(plan, fromDate, toDate)) {
                continue;
            }
            plans.add(toPlanItem(plan));
        }
        plans.sort(Comparator.comparing(MarketDataAssetRelatedTasksVO.RelatedTaskItem::startedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<MarketDataSyncTaskItemDO> items = taskItemMapper.selectBySymbol(canonicalSymbol, RUN_FETCH_LIMIT, 0);
        List<MarketDataAssetRelatedTasksVO.RelatedTaskItem> allRuns = new ArrayList<>();
        for (MarketDataSyncTaskItemDO item : items) {
            MarketDataSyncPlanDO plan = item.getPlanId() == null ? null : planById.get(item.getPlanId());
            if (!matchesInterval(plan == null ? null : plan.getIntervalType(), interval)) {
                continue;
            }
            if (!itemOverlapsRange(item, fromDate, toDate)) {
                continue;
            }
            allRuns.add(toRunItem(item, plan));
        }
        allRuns.sort(Comparator.comparing(MarketDataAssetRelatedTasksVO.RelatedTaskItem::startedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(page, 1) - 1) * safeSize;
        List<MarketDataAssetRelatedTasksVO.RelatedTaskItem> runs = offset >= allRuns.size()
                ? List.of()
                : allRuns.subList(offset, Math.min(offset + safeSize, allRuns.size()));

        return new MarketDataAssetRelatedTasksVO(securityMeta.toSecurityVO(security), plans, runs);
    }

    private Map<Long, MarketDataSyncPlanDO> loadPlansById() {
        Map<Long, MarketDataSyncPlanDO> byId = new HashMap<>();
        List<MarketDataSyncPlanDO> plans = syncPlanMapper.selectByFilter(null, null, null, PLAN_FETCH_LIMIT, 0);
        if (plans != null) {
            for (MarketDataSyncPlanDO plan : plans) {
                byId.put(plan.getId(), plan);
            }
        }
        return byId;
    }

    /** scope_json 结构化解析 + 兜底子串匹配是否覆盖该证券。 */
    private boolean scopeContainsSymbol(String scopeJson, String canonicalSymbol) {
        if (scopeJson == null || scopeJson.isBlank()) {
            return false;
        }
        if (scopeJson.contains(canonicalSymbol)) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(scopeJson);
            JsonNode symbolNode = root.get("canonicalSymbol");
            if (symbolNode != null && canonicalSymbol.equals(symbolNode.asText())) {
                return true;
            }
            JsonNode symbols = root.get("symbols");
            if (symbols != null && symbols.isArray()) {
                for (JsonNode node : symbols) {
                    if (canonicalSymbol.equals(node.asText())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesInterval(String actual, String filter) {
        return filter == null || filter.isBlank() || filter.equals(actual);
    }

    private boolean planOverlapsRange(MarketDataSyncPlanDO plan, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        LocalDate planStart = scopeDate(plan.getScopeJson(), "startDate");
        LocalDate planEnd = scopeDate(plan.getScopeJson(), "endDate");
        if (planStart == null && planEnd == null) {
            return true;
        }
        return rangesOverlap(planStart, planEnd, from, to);
    }

    private boolean itemOverlapsRange(MarketDataSyncTaskItemDO item, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        LocalDateTime started = item.getStartedAt() != null ? item.getStartedAt() : item.getCreatedAt();
        if (started == null) {
            return false;
        }
        LocalDate day = started.toLocalDate();
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    /** 两个（可能无界）闭区间是否有重叠：任一区间端点为 null 表示无界。 */
    private boolean rangesOverlap(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
        boolean strictlyBefore = (b != null && c != null && b.isBefore(c))
                || (d != null && a != null && d.isBefore(a));
        return !strictlyBefore;
    }

    /** 从 scope_json 取日期字段（容错：解析失败返回 null）。 */
    private LocalDate scopeDate(String scopeJson, String field) {
        if (scopeJson == null || scopeJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(scopeJson).get(field);
            if (node == null || node.isNull()) {
                return null;
            }
            return LocalDate.parse(node.asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    private MarketDataAssetRelatedTasksVO.RelatedTaskItem toPlanItem(MarketDataSyncPlanDO plan) {
        return new MarketDataAssetRelatedTasksVO.RelatedTaskItem(
                "PLAN",
                plan.getId(),
                plan.getPlanName() != null ? plan.getPlanName() : ("采集计划 #" + plan.getId()),
                plan.getTaskType(),
                plan.getIntervalType(),
                Boolean.TRUE.equals(plan.getEnabled()) ? "ENABLED" : "DISABLED",
                MarketDataAssetTimeFormatter.dateText(scopeDate(plan.getScopeJson(), "startDate")),
                MarketDataAssetTimeFormatter.dateText(scopeDate(plan.getScopeJson(), "endDate")),
                MarketDataAssetTimeFormatter.formatStoredTime(plan.getLastRunAt()),
                null, null, null);
    }

    private MarketDataAssetRelatedTasksVO.RelatedTaskItem toRunItem(MarketDataSyncTaskItemDO item,
                                                                    MarketDataSyncPlanDO plan) {
        String name = plan != null && plan.getPlanName() != null
                ? plan.getPlanName() + "（记录 #" + item.getId() + "）"
                : ("采集记录 #" + item.getId());
        return new MarketDataAssetRelatedTasksVO.RelatedTaskItem(
                "RUN",
                item.getId(),
                name,
                plan != null ? plan.getTaskType() : null,
                plan != null ? plan.getIntervalType() : null,
                item.getStatus(),
                MarketDataAssetTimeFormatter.dateText(scopeDate(item.getScopeDetail(), "startDate")),
                MarketDataAssetTimeFormatter.dateText(scopeDate(item.getScopeDetail(), "endDate")),
                MarketDataAssetTimeFormatter.formatStoredTime(item.getStartedAt()),
                MarketDataAssetTimeFormatter.formatStoredTime(item.getFinishedAt()),
                item.getErrorCode(),
                item.getErrorMessage());
    }
}
