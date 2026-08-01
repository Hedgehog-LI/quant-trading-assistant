package com.quant.trade.marketdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.constant.SecurityDirectoryConstants;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.dao.SecurityDirectorySyncStateMapper;
import com.quant.trade.marketdata.dao.StockAliasMapper;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dao.SyncScopeLockMapper;
import com.quant.trade.marketdata.model.MarketDataSyncTaskDO;
import com.quant.trade.marketdata.model.SecurityDirectorySyncStateDO;
import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.provider.DirectorySnapshotIdentity;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider.DirectorySnapshot;
import com.quant.trade.marketdata.provider.SecurityDirectoryProviderException;
import com.quant.trade.marketdata.util.SecurityDirectoryIdentityCalculator;
import com.quant.trade.marketdata.util.SecurityTextNormalizer;
import com.quant.trade.marketdata.vo.MarketDataSyncTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 证券目录同步服务（D3）。五阶段：parse → validate → staging/diff → 质量门禁 → 原子发布。
 * <p>
 * 复用 market_data_sync_task(SECURITY_MASTER_SYNC) 记录执行过程；SyncScopeLockMapper 行锁防同范围
 * 并发 sibling；selectLatestByScope 做 PENDING/RUNNING/SUCCEEDED 幂等短路；FAILED 时 retry 建立
 * parent_task_id。同步失败只更新 task=FAILED 与错误摘要，不清空 stock_basic/stock_alias，保留上一成功目录。
 * 单次目录缺失不得直接判定退市；质量门禁失败整批不发布且不修改 list_status。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityDirectorySyncService {

    private final SecurityDirectoryProvider provider;
    private final SecurityDirectorySyncStateMapper syncStateMapper;
    private final MarketDataSyncTaskMapper taskMapper;
    private final SyncScopeLockMapper syncScopeLockMapper;
    private final StockBasicMapper stockBasicMapper;
    private final StockAliasMapper stockAliasMapper;
    private final ObjectMapper objectMapper;
    private final Clock marketDataClock;
    @Qualifier("txRequiresNew")
    private final TransactionTemplate txRequiresNew;
    private final double rowCountSwingThreshold;

    /** 触发同步，返回 task VO。provider disabled 抛 BUSINESS_RULE_VIOLATION（由 controller 转 HTTP 400）。 */
    public MarketDataSyncTaskVO trigger(String mode) {
        if (!provider.isEnabled()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "证券目录同步 provider 未启用");
        }
        String effectiveMode = mode == null || mode.isBlank()
                ? SecurityDirectoryConstants.MODE_FULL : mode.toUpperCase();
        DirectorySnapshot snapshot = provider.fetch(effectiveMode);
        return runSync(effectiveMode, snapshot);
    }

    /** scheduler 测试 seam：按指定模式触发同步（已被 provider disabled 时记 FAILED 任务）。 */
    public MarketDataSyncTaskVO triggerScheduled(String mode) {
        return trigger(mode);
    }

    private MarketDataSyncTaskVO runSync(String mode, DirectorySnapshot snapshot) {
        List<StockAliasDO> allAliases = new ArrayList<>();
        snapshot.rows().forEach(row -> allAliases.addAll(row.aliases()));
        String snapshotHash = com.quant.trade.marketdata.util.SecurityDirectoryIdentityCalculator
                .computeSnapshotHash(snapshot.providerCode(), mode, snapshot.stocks(), allAliases);
        DirectorySnapshotIdentity identity = new DirectorySnapshotIdentity(
                com.quant.trade.marketdata.util.SecurityDirectoryIdentityCalculator.snapshotIdFromHash(snapshotHash),
                snapshotHash, snapshot.providerCode() + "@"
                        + com.quant.trade.marketdata.util.SecurityDirectoryIdentityCalculator.snapshotIdFromHash(snapshotHash));
        String scopeJson = buildScopeJson(snapshot.providerCode(), identity, mode);
        String scopeHash = UUID.nameUUIDFromBytes(scopeJson.getBytes()).toString();

        // 1. 快速无锁检查：已有 PENDING/RUNNING/SUCCEEDED 直接返回（幂等短路）。
        MarketDataSyncTaskDO latest = taskMapper.selectLatestByScope(
                snapshot.providerCode(), SecurityDirectoryConstants.TASK_TYPE_SECURITY_MASTER_SYNC, scopeJson);
        if (latest != null && isNonTerminalOrSucceeded(latest.getStatus())) {
            return toTaskVO(latest);
        }

        // 2. 进入 scope 级行锁临界区，防止并发 sibling retry。
        MarketDataSyncTaskDO task = txRequiresNew.execute(status -> {
            syncScopeLockMapper.upsert(snapshot.providerCode(),
                    SecurityDirectoryConstants.TASK_TYPE_SECURITY_MASTER_SYNC, scopeHash);
            syncScopeLockMapper.selectForUpdate(snapshot.providerCode(),
                    SecurityDirectoryConstants.TASK_TYPE_SECURITY_MASTER_SYNC, scopeHash);
            MarketDataSyncTaskDO rechecked = taskMapper.selectLatestByScope(snapshot.providerCode(),
                    SecurityDirectoryConstants.TASK_TYPE_SECURITY_MASTER_SYNC, scopeJson);
            if (rechecked != null && isNonTerminalOrSucceeded(rechecked.getStatus())) {
                return rechecked;
            }
            Long parentTaskId = rechecked != null ? rechecked.getId() : null;
            String idemKey = parentTaskId != null
                    ? UUID.nameUUIDFromBytes((scopeHash + "#retry#" + parentTaskId + "#" + System.nanoTime())
                            .getBytes()).toString()
                    : UUID.nameUUIDFromBytes((snapshot.providerCode()
                            + SecurityDirectoryConstants.TASK_TYPE_SECURITY_MASTER_SYNC
                            + identity.snapshotHash() + mode).getBytes()).toString();
            MarketDataSyncTaskDO created = MarketDataSyncTaskDO.builder()
                    .taskType(SecurityDirectoryConstants.TASK_TYPE_SECURITY_MASTER_SYNC)
                    .provider(snapshot.providerCode())
                    .scopeJson(scopeJson)
                    .status(MarketDataConstants.TASK_STATUS_PENDING)
                    .idempotencyKey(idemKey)
                    .parentTaskId(parentTaskId)
                    .build();
            taskMapper.insert(created);
            return created;
        });
        Long taskId = task.getId();

        // 3. 转 RUNNING（独立事务）。
        txRequiresNew.executeWithoutResult(status -> {
            MarketDataSyncTaskDO running = new MarketDataSyncTaskDO();
            running.setId(taskId);
            running.setStatus(MarketDataConstants.TASK_STATUS_RUNNING);
            running.setStartedAt(LocalDateTime.now(marketDataClock));
            taskMapper.updateById(running);
        });

        // 4. 执行五阶段。发布在独立新事务内（txRequiresNew），任一阶段或发布失败整批回滚，
        //    保留上一成功目录；不能依赖 self-invocation 的 @Transactional（Spring 代理不拦截自调用）。
        try {
            PublishResult result = txRequiresNew.execute(status -> publish(snapshot, identity));
            txRequiresNew.executeWithoutResult(status -> {
                MarketDataSyncTaskDO done = new MarketDataSyncTaskDO();
                done.setId(taskId);
                done.setStatus(MarketDataConstants.TASK_STATUS_SUCCEEDED);
                done.setTotalCount(result.total);
                done.setInsertedCount(result.inserted);
                done.setUpdatedCount(result.updated);
                done.setSkippedCount(result.unchanged);
                done.setSuccessCount(result.inserted + result.updated + result.unchanged);
                done.setFailCount(0);
                done.setFinishedAt(LocalDateTime.now(marketDataClock));
                taskMapper.updateById(done);
            });
            recordSyncStateSuccess(snapshot.providerCode(), identity, mode, result);
        } catch (SecurityDirectorySyncException exception) {
            markFailed(taskId, exception);
            recordSyncStateFailure(snapshot.providerCode(), exception);
            throw exception.toBusiness();
        } catch (RuntimeException exception) {
            SecurityDirectorySyncException wrapped = new SecurityDirectorySyncException(
                    ErrorCodeEnum.INTERNAL_ERROR, "PERSISTENCE_FAILED", "目录写入失败，整批已回滚",
                    "{\"error\":\"" + sanitize(exception.getMessage()) + "\"}");
            markFailed(taskId, wrapped);
            recordSyncStateFailure(snapshot.providerCode(), wrapped);
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "证券目录同步异常");
        }
        return toTaskVO(taskMapper.selectById(taskId));
    }

    /** 五阶段发布：校验 → staging/diff → 质量门禁 → 原子发布（由 runSync 经 txRequiresNew 包裹单事务）。 */
    PublishResult publish(DirectorySnapshot snapshot, DirectorySnapshotIdentity identity) {
        List<StockBasicDO> candidates = snapshot.stocks();
        Map<String, List<StockAliasDO>> aliasesBySymbol = aliasesBySymbol(snapshot.rows());
        // 质量门禁：非空快照。
        if (candidates.isEmpty()) {
            throw gateFailure(ErrorCodeEnum.MARKET_DATA_EMPTY_RESULT,
                    SecurityDirectoryConstants.GATE_EMPTY_SNAPSHOT, null);
        }
        // 质量门禁：必填字段（D1 REQUIRED）+ 候选集内 alias identity 唯一性。
        validateRequiredFields(candidates);
        validateAliasUniqueness(aliasesBySymbol);
        // staging/diff：按 canonical_symbol 预加载现有目录，计算 inserted/updated/unchanged。
        Map<String, StockBasicDO> existingStocks = preloadStocks(candidates);
        Map<Long, Map<String, StockAliasDO>> existingAliases = preloadAliases(existingStocks.values());
        List<StockBasicDO> toInsert = new ArrayList<>();
        List<StockBasicDO> toUpdate = new ArrayList<>();
        int unchanged = 0;
        for (StockBasicDO candidate : candidates) {
            StockBasicDO existing = existingStocks.get(candidate.getCanonicalSymbol());
            if (existing == null) {
                toInsert.add(candidate);
            } else if (SecurityDirectoryIdentityCalculator.sameDirectoryData(existing, candidate)) {
                unchanged++;
            } else {
                candidate.setId(existing.getId());
                toUpdate.add(candidate);
            }
        }
        // 质量门禁：数量波动阈值（候选发布集行数相对当前完整目录总数）。
        long previousCount = stockBasicMapper.countAll();
        long candidateCount = candidates.size();
        if (previousCount > 0) {
            double swing = Math.abs(candidateCount - previousCount) / (double) previousCount;
            if (Double.compare(swing, rowCountSwingThreshold) >= 0) {
                throw gateFailure(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                        SecurityDirectoryConstants.GATE_ROW_COUNT_SWING,
                        "{\"gate\":\"" + SecurityDirectoryConstants.GATE_ROW_COUNT_SWING
                                + "\",\"threshold\":" + rowCountSwingThreshold
                                + ",\"previousCount\":" + previousCount
                                + ",\"candidateCount\":" + candidateCount + "}");
            }
        }
        // 原子发布（单事务）：insert/update/alias upsert。任一失败整批回滚，保留上一成功目录。
        long inserted = 0;
        long updated = 0;
        for (StockBasicDO candidate : toInsert) {
            stockBasicMapper.insertDirectory(candidate);
            existingStocks.put(candidate.getCanonicalSymbol(), candidate);
            existingAliases.put(candidate.getId(), new LinkedHashMap<>());
            inserted++;
            for (StockAliasDO alias : aliasesBySymbol.getOrDefault(candidate.getCanonicalSymbol(), List.of())) {
                alias.setStockBasicId(candidate.getId());
                upsertAlias(alias, existingAliases);
            }
        }
        for (StockBasicDO candidate : toUpdate) {
            // former-name：旧非空 name 与新 name 不同时插入恰好一条 FORMER_NAME（D1 语义）。
            StockBasicDO existing = existingStocks.get(candidate.getCanonicalSymbol());
            if (existing.getName() != null && !existing.getName().isBlank()
                    && !Objects.equals(existing.getName(), candidate.getName())) {
                StockAliasDO former = StockAliasDO.builder()
                        .stockBasicId(existing.getId())
                        .alias(existing.getName())
                        .normalizedAlias(SecurityTextNormalizer.normalize(existing.getName()))
                        .aliasType("FORMER_NAME")
                        .dataSource(candidate.getDataSource())
                        .build();
                upsertAlias(former, existingAliases);
            }
            stockBasicMapper.updateDirectoryById(candidate);
            existingStocks.put(candidate.getCanonicalSymbol(), candidate);
            updated++;
            for (StockAliasDO alias : aliasesBySymbol.getOrDefault(candidate.getCanonicalSymbol(), List.of())) {
                alias.setStockBasicId(candidate.getId());
                upsertAlias(alias, existingAliases);
            }
        }
        // unchanged 候选的 alias 也幂等 upsert（与 D1 persist 行为一致）。
        for (StockBasicDO candidate : candidates) {
            StockBasicDO existing = existingStocks.get(candidate.getCanonicalSymbol());
            if (existing != null && SecurityDirectoryIdentityCalculator.sameDirectoryData(existing, candidate)) {
                for (StockAliasDO alias : aliasesBySymbol.getOrDefault(candidate.getCanonicalSymbol(), List.of())) {
                    alias.setStockBasicId(existing.getId());
                    upsertAlias(alias, existingAliases);
                }
            }
        }
        return new PublishResult(candidates.size(), (int) inserted, (int) updated, unchanged
                + (int) snapshot.duplicateUnchanged());
    }

    private Map<String, List<StockAliasDO>> aliasesBySymbol(
            List<com.quant.trade.marketdata.provider.SecurityDirectoryProvider.SnapshotRow> rows) {
        Map<String, List<StockAliasDO>> result = new LinkedHashMap<>();
        for (com.quant.trade.marketdata.provider.SecurityDirectoryProvider.SnapshotRow row : rows) {
            result.put(row.stock().getCanonicalSymbol(), row.aliases());
        }
        return result;
    }

    private void upsertAlias(StockAliasDO alias, Map<Long, Map<String, StockAliasDO>> existingAliases) {
        alias.setNormalizedAliasKey(SecurityTextNormalizer.identityKey(alias.getNormalizedAlias()));
        Map<String, StockAliasDO> aliases = existingAliases.computeIfAbsent(
                alias.getStockBasicId(), ignored -> new LinkedHashMap<>());
        String key = alias.getAliasType() + "|" + alias.getNormalizedAlias();
        if (aliases.containsKey(key)) {
            return;
        }
        StockAliasDO persisted = stockAliasMapper.selectByIdentity(
                alias.getStockBasicId(), alias.getNormalizedAliasKey(), alias.getAliasType());
        if (persisted != null) {
            aliases.put(key, persisted);
            return;
        }
        stockAliasMapper.insert(alias);
        aliases.put(key, alias);
    }

    private void validateRequiredFields(List<StockBasicDO> candidates) {
        // 必填字段检查（D1 REQUIRED）。
        for (StockBasicDO stock : candidates) {
            if (isBlank(stock.getCanonicalSymbol()) || isBlank(stock.getName())
                    || isBlank(stock.getMarket()) || isBlank(stock.getExchange())
                    || isBlank(stock.getCurrency()) || isBlank(stock.getSecurityType())
                    || isBlank(stock.getListStatus()) || isBlank(stock.getDataSource())
                    || stock.getSourceUpdatedAt() == null) {
                throw gateFailure(ErrorCodeEnum.DAILY_BAR_VALIDATION_ERROR,
                        SecurityDirectoryConstants.GATE_REQUIRED_FIELD,
                        "{\"gate\":\"" + SecurityDirectoryConstants.GATE_REQUIRED_FIELD
                                + "\",\"sample\":\"" + sanitize(stock.getCanonicalSymbol()) + "\"}");
            }
        }
    }

    private void validateAliasUniqueness(Map<String, List<StockAliasDO>> aliasesBySymbol) {
        // 候选集内 alias identity 不重复：同一 (aliasType, normalizedAlias) 不得归属多只证券。
        Map<String, List<String>> owners = new LinkedHashMap<>();
        aliasesBySymbol.forEach((symbol, aliases) -> {
            for (StockAliasDO alias : aliases) {
                String key = alias.getAliasType() + "|" + alias.getNormalizedAlias();
                owners.computeIfAbsent(key, ignored -> new ArrayList<>()).add(symbol);
            }
        });
        List<String> conflicts = owners.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + "@[" + String.join(",", entry.getValue()) + "]")
                .limit(10)
                .toList();
        if (!conflicts.isEmpty()) {
            throw gateFailure(ErrorCodeEnum.DAILY_BAR_VALIDATION_ERROR,
                    SecurityDirectoryConstants.GATE_UNIQUENESS,
                    "{\"gate\":\"" + SecurityDirectoryConstants.GATE_UNIQUENESS
                            + "\",\"conflicts\":" + objectMapper.valueToTree(conflicts).toString() + "}");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, StockBasicDO> preloadStocks(List<StockBasicDO> candidates) {
        Map<String, StockBasicDO> result = new LinkedHashMap<>();
        List<String> symbols = candidates.stream().map(StockBasicDO::getCanonicalSymbol).toList();
        for (int start = 0; start < symbols.size(); start += 500) {
            List<String> part = symbols.subList(start, Math.min(start + 500, symbols.size()));
            stockBasicMapper.selectByCanonicalSymbols(part)
                    .forEach(stock -> result.put(stock.getCanonicalSymbol(), stock));
        }
        return result;
    }

    private Map<Long, Map<String, StockAliasDO>> preloadAliases(java.util.Collection<StockBasicDO> stocks) {
        List<Long> ids = new ArrayList<>();
        stocks.forEach(stock -> ids.add(stock.getId()));
        Map<Long, Map<String, StockAliasDO>> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, new LinkedHashMap<>()));
        for (int start = 0; start < ids.size(); start += 500) {
            List<Long> part = ids.subList(start, Math.min(start + 500, ids.size()));
            stockAliasMapper.selectByStockBasicIds(part).forEach(alias ->
                    result.computeIfAbsent(alias.getStockBasicId(), ignored -> new LinkedHashMap<>())
                            .put(alias.getAliasType() + "|" + alias.getNormalizedAlias(), alias));
        }
        return result;
    }

    private void markFailed(Long taskId, SecurityDirectorySyncException exception) {
        txRequiresNew.executeWithoutResult(status -> {
            MarketDataSyncTaskDO failed = new MarketDataSyncTaskDO();
            failed.setId(taskId);
            failed.setStatus(MarketDataConstants.TASK_STATUS_FAILED);
            failed.setFailCount(1);
            failed.setFinishedAt(LocalDateTime.now(marketDataClock));
            failed.setLastErrorCode(exception.errorCode.getCode());
            failed.setErrorSummaryJson(exception.errorSummary);
            taskMapper.updateById(failed);
        });
    }

    private void recordSyncStateSuccess(String providerCode, DirectorySnapshotIdentity identity,
                                        String mode, PublishResult result) {
        txRequiresNew.executeWithoutResult(status -> {
            SecurityDirectorySyncStateDO record = SecurityDirectorySyncStateDO.builder()
                    .provider(providerCode)
                    .lastSnapshotId(identity.snapshotId())
                    .lastSnapshotHash(identity.snapshotHash())
                    .lastMode(mode)
                    .lastSuccessAt(LocalDateTime.now(marketDataClock))
                    .lastInsertedCount(result.inserted)
                    .lastUpdatedCount(result.updated)
                    .lastUnchangedCount(result.unchanged)
                    .lastErrorCode(null)
                    .lastErrorSummary(null)
                    .build();
            syncStateMapper.upsertByProvider(record);
        });
    }

    private void recordSyncStateFailure(String providerCode, SecurityDirectorySyncException exception) {
        txRequiresNew.executeWithoutResult(status -> {
            SecurityDirectorySyncStateDO existing = syncStateMapper.selectByProvider(providerCode);
            SecurityDirectorySyncStateDO.SecurityDirectorySyncStateDOBuilder builder = SecurityDirectorySyncStateDO.builder()
                    .provider(providerCode)
                    .lastErrorCode(exception.errorCode.getCode())
                    .lastErrorSummary(truncate(exception.errorSummary, 1024));
            if (existing != null) {
                builder.lastSnapshotId(existing.getLastSnapshotId())
                        .lastSnapshotHash(existing.getLastSnapshotHash())
                        .lastMode(existing.getLastMode())
                        .lastSuccessAt(existing.getLastSuccessAt())
                        .lastInsertedCount(existing.getLastInsertedCount())
                        .lastUpdatedCount(existing.getLastUpdatedCount())
                        .lastUnchangedCount(existing.getLastUnchangedCount());
            }
            syncStateMapper.upsertByProvider(builder.build());
        });
    }

    private String buildScopeJson(String providerCode, DirectorySnapshotIdentity identity, String mode) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put(SecurityDirectoryConstants.SCOPE_PROVIDER, providerCode);
        scope.put(SecurityDirectoryConstants.SCOPE_SNAPSHOT_ID, identity.snapshotId());
        scope.put(SecurityDirectoryConstants.SCOPE_SNAPSHOT_HASH, identity.snapshotHash());
        scope.put(SecurityDirectoryConstants.SCOPE_MODE, mode);
        try {
            return objectMapper.writeValueAsString(scope);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "scope 序列化失败");
        }
    }

    private static boolean isNonTerminalOrSucceeded(String status) {
        return MarketDataConstants.TASK_STATUS_PENDING.equals(status)
                || MarketDataConstants.TASK_STATUS_RUNNING.equals(status)
                || MarketDataConstants.TASK_STATUS_SUCCEEDED.equals(status);
    }

    public MarketDataSyncTaskVO getTask(Long taskId) {
        if (taskId == null) {
            return null;
        }
        return toTaskVO(taskMapper.selectById(taskId));
    }

    private MarketDataSyncTaskVO toTaskVO(MarketDataSyncTaskDO task) {
        if (task == null) {
            return null;
        }
        return new MarketDataSyncTaskVO(task.getId(), task.getTaskType(), task.getProvider(), task.getScopeJson(),
                task.getStatus(), task.getTotalCount(), task.getSuccessCount(),
                task.getFailCount(), task.getInsertedCount(), task.getUpdatedCount(), task.getSkippedCount(),
                task.getStartedAt(), task.getFinishedAt(), task.getLastErrorCode(), task.getErrorSummaryJson(),
                task.getParentTaskId(), task.getCreatedAt());
    }

    private SecurityDirectorySyncException gateFailure(ErrorCodeEnum code, String gate, String summary) {
        String effectiveSummary = summary != null ? summary
                : "{\"gate\":\"" + gate + "\"}";
        return new SecurityDirectorySyncException(code, gate,
                "证券目录同步质量门禁失败: " + gate, effectiveSummary);
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 发布结果。 */
    record PublishResult(int total, int inserted, int updated, int unchanged) {
    }

    /** 同步异常，携带稳定 reasonCode 与错误摘要 JSON。 */
    static final class SecurityDirectorySyncException extends RuntimeException {
        final ErrorCodeEnum errorCode;
        final String reasonCode;
        final String errorSummary;

        SecurityDirectorySyncException(ErrorCodeEnum errorCode, String reasonCode, String message, String errorSummary) {
            super(message);
            this.errorCode = errorCode;
            this.reasonCode = reasonCode;
            this.errorSummary = errorSummary;
        }

        BusinessException toBusiness() {
            return new BusinessException(errorCode, getMessage());
        }
    }
}
