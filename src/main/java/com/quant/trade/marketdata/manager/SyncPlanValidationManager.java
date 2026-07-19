package com.quant.trade.marketdata.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** 采集计划产品语义校验；创建、编辑、手工执行和 scheduler 共用同一口径。 */
@Component
@RequiredArgsConstructor
public class SyncPlanValidationManager {

    private static final List<String> MINUTE_INTERVALS = List.of("1M", "5M", "15M", "30M", "60M");
    private final ObjectMapper objectMapper;

    public ValidationResult inspect(MarketDataSyncPlanDO plan) {
        List<String> errors = new ArrayList<>();
        PlanScope scope = parseScope(plan == null ? null : plan.getScopeJson(), errors);
        if (plan == null) {
            errors.add("采集计划不能为空");
            return new ValidationResult(scope, errors, false, false);
        }
        String taskType = upper(plan.getTaskType());
        String trigger = upper(plan.getTriggerType());
        String provider = upper(plan.getProvider());
        String interval = upper(plan.getIntervalType());
        String adjust = upper(plan.getAdjustType());

        if (!List.of("LONGPORT", "FAKE").contains(provider)) {
            errors.add("provider 仅支持 LONGPORT（FAKE 只用于受控验收）");
        }
        if ("LONGPORT".equals(provider) && "HF".equals(adjust)) {
            errors.add("LongPort Java SDK 4.3.3 不支持 HF 后复权");
        }

        boolean manual = false;
        boolean automatic = false;
        switch (taskType) {
            case WorkbenchConstants.TASK_DAILY_BAR_BACKFILL -> {
                requireDateRange(scope, errors);
                if (!WorkbenchConstants.TRIGGER_MANUAL.equals(trigger)) {
                    errors.add("DAILY_BAR_BACKFILL 当前只允许 MANUAL");
                }
                manual = errors.isEmpty();
            }
            case WorkbenchConstants.TASK_MINUTE_BAR_BACKFILL -> {
                requireDateRange(scope, errors);
                requireMinuteInterval(interval, errors);
                if (scope.symbols().stream().anyMatch(symbol -> !isAShare(symbol))) {
                    errors.add("港股/美股分钟 K 的交易时段与质量规则尚未闭环，当前只支持 SH/SZ/BJ");
                }
                if (!WorkbenchConstants.TRIGGER_MANUAL.equals(trigger)) {
                    errors.add("MINUTE_BAR_BACKFILL 只允许 MANUAL；盘中持续采集请使用 INTRADAY_MINUTE_REFRESH");
                }
                manual = errors.isEmpty();
            }
            case WorkbenchConstants.TASK_INTRADAY_MINUTE_REFRESH -> {
                requireMinuteInterval(interval, errors);
                if (!WorkbenchConstants.TRIGGER_INTRADAY.equals(trigger)) {
                    errors.add("INTRADAY_MINUTE_REFRESH 的 triggerType 必须为 INTRADAY");
                }
                if (frequencySeconds(plan.getCollectFrequency()) == null) {
                    errors.add("INTRADAY_MINUTE_REFRESH 必须配置采集频率 30S/60S/5M");
                }
                if (scope.symbols().stream().anyMatch(symbol -> !isAShare(symbol))) {
                    errors.add("港股/美股交易日历与时区尚未闭环，自动盘中任务当前只支持 SH/SZ/BJ");
                }
                automatic = errors.isEmpty();
            }
            default -> errors.add("当前执行引擎不支持任务类型: " + taskType);
        }
        return new ValidationResult(scope, errors, manual && errors.isEmpty(), automatic && errors.isEmpty());
    }

    public ValidationResult validate(MarketDataSyncPlanDO plan) {
        ValidationResult result = inspect(plan);
        if (!result.errors().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PLAN_INVALID,
                    String.join("；", result.errors()));
        }
        return result;
    }

    public Integer frequencySeconds(String frequency) {
        if (frequency == null || frequency.isBlank()) return null;
        return switch (frequency.trim().toUpperCase(Locale.ROOT)) {
            case "30", "30S" -> 30;
            case "60", "60S", "1M" -> 60;
            case "300", "300S", "5M" -> 300;
            default -> null;
        };
    }

    private PlanScope parseScope(String scopeJson, List<String> errors) {
        if (scopeJson == null || scopeJson.isBlank()) {
            errors.add("至少配置一个 canonical symbol");
            return new PlanScope(List.of(), null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(scopeJson);
            LinkedHashSet<String> symbols = new LinkedHashSet<>();
            if (root.hasNonNull("canonicalSymbol")) addSymbol(symbols, root.get("canonicalSymbol").asText(), errors);
            if (root.has("symbols") && root.get("symbols").isArray()) {
                root.get("symbols").forEach(node -> addSymbol(symbols, node.asText(), errors));
            }
            if (symbols.isEmpty()) errors.add("至少配置一个 canonical symbol");
            if (symbols.size() > WorkbenchConstants.MAX_PLAN_SYMBOLS) {
                errors.add("单个计划最多配置 " + WorkbenchConstants.MAX_PLAN_SYMBOLS + " 个标的，不允许全市场扫描");
            }
            LocalDate start = parseDate(root, "startDate", errors);
            LocalDate end = parseDate(root, "endDate", errors);
            if (start != null && end != null && start.isAfter(end)) errors.add("startDate 不能晚于 endDate");
            return new PlanScope(List.copyOf(symbols), start, end);
        } catch (Exception exception) {
            errors.add("scopeJson 必须是合法 JSON");
            return new PlanScope(List.of(), null, null);
        }
    }

    private void addSymbol(LinkedHashSet<String> symbols, String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) return;
        try {
            symbols.add(CanonicalSymbolUtils.normalize(raw));
        } catch (IllegalArgumentException exception) {
            errors.add("canonical symbol 不合法: " + raw);
        }
    }

    private LocalDate parseDate(JsonNode root, String field, List<String> errors) {
        if (!root.hasNonNull(field) || root.get(field).asText().isBlank()) return null;
        try {
            return LocalDate.parse(root.get(field).asText());
        } catch (Exception exception) {
            errors.add(field + " 必须为 YYYY-MM-DD");
            return null;
        }
    }

    private void requireDateRange(PlanScope scope, List<String> errors) {
        if (scope.startDate() == null) errors.add("历史补档必须配置 startDate");
        if (scope.endDate() == null) errors.add("历史补档必须配置 endDate");
    }

    private void requireMinuteInterval(String interval, List<String> errors) {
        if (!MINUTE_INTERVALS.contains(interval)) errors.add("分钟任务粒度必须为 1M/5M/15M/30M/60M");
    }

    private boolean isAShare(String symbol) {
        return symbol.startsWith("SH.") || symbol.startsWith("SZ.") || symbol.startsWith("BJ.");
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record PlanScope(List<String> symbols, LocalDate startDate, LocalDate endDate) {}
    public record ValidationResult(PlanScope scope, List<String> errors,
                                   boolean manuallyRunnable, boolean automaticallyRunnable) {}
}
