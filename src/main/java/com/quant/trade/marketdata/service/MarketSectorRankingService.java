package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketSectorCollectionConstants;
import com.quant.trade.marketdata.dao.MarketSectorRankingBatchMapper;
import com.quant.trade.marketdata.dao.MarketSectorRankingConfigMapper;
import com.quant.trade.marketdata.dao.MarketSectorRankingItemMapper;
import com.quant.trade.marketdata.dto.UpdateMarketSectorRankingConfigDTO;
import com.quant.trade.marketdata.manager.MarketSectorRankingPersistenceManager;
import com.quant.trade.marketdata.manager.MarketSectorScheduleManager;
import com.quant.trade.marketdata.manager.MarketSectorScheduleManager.CollectionWindow;
import com.quant.trade.marketdata.model.MarketSectorRankingBatchDO;
import com.quant.trade.marketdata.model.MarketSectorRankingConfigDO;
import com.quant.trade.marketdata.model.MarketSectorRankingItemDO;
import com.quant.trade.marketdata.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 全市场板块排行配置、采集和历史查询服务。 */
@Service
@RequiredArgsConstructor
public class MarketSectorRankingService {
    private static final int MAX_PAGE_SIZE = 200;

    private final MarketSectorRankingConfigMapper configMapper;
    private final MarketSectorRankingBatchMapper batchMapper;
    private final MarketSectorRankingItemMapper itemMapper;
    private final MarketSectorCatalogService catalogService;
    private final MarketSectorRankingPersistenceManager persistenceManager;
    private final MarketSectorScheduleManager scheduleManager;
    private final Clock marketDataClock;

    public List<MarketSectorRankingConfigVO> configs() {
        return configMapper.selectAll().stream().map(this::toConfigVO).toList();
    }

    public MarketSectorRankingConfigVO updateConfig(String market,
                                                     UpdateMarketSectorRankingConfigDTO dto) {
        String normalizedMarket = normalizeMarket(market);
        if (!MarketSectorCollectionConstants.SUPPORTED_INTERVAL_MINUTES
                .contains(dto.intradayIntervalMinutes())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "盘中频率仅支持收盘、5、10、15、30、60 分钟");
        }
        MarketSectorRankingConfigDO config = requireConfig(normalizedMarket);
        config.setEnabled(dto.enabled());
        config.setIntradayIntervalMinutes(dto.intradayIntervalMinutes());
        config.setCloseSnapshotEnabled(dto.closeSnapshotEnabled());
        config.setRankLimit(dto.rankLimit());
        configMapper.updateConfig(config);
        return toConfigVO(requireConfig(normalizedMarket));
    }

    public MarketSectorRankingBatchVO collectNow(String market) {
        MarketSectorRankingConfigDO config = requireConfig(normalizeMarket(market));
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        CollectionWindow window = new CollectionWindow(MarketSectorCollectionConstants.SNAPSHOT_MANUAL,
                scheduleManager.tradeDate(config.getMarketCode(), marketDataClock.instant()),
                scheduleManager.manualBucket(marketDataClock.instant(), marketDataClock.getZone()));
        return collect(config, window, now);
    }

    public MarketSectorRankingBatchVO collectScheduled(MarketSectorRankingConfigDO config,
                                                        CollectionWindow window) {
        return collect(config, window, LocalDateTime.now(marketDataClock));
    }

    public PageResultVO<MarketSectorRankingBatchVO> history(String market, LocalDate tradeDate,
                                                            String snapshotType, int page, int size) {
        String normalizedMarket = market == null || market.isBlank() ? null : normalizeMarket(market);
        String normalizedType = normalizeSnapshotType(snapshotType);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var items = batchMapper.selectByFilter(normalizedMarket, tradeDate, normalizedType, safeSize,
                (safePage - 1) * safeSize).stream().map(this::toBatchVO).toList();
        return PageResultVO.of(items, batchMapper.countByFilter(normalizedMarket, tradeDate, normalizedType),
                safePage, safeSize);
    }

    public List<MarketSectorRankingItemVO> items(Long batchId) {
        if (batchMapper.selectById(batchId) == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "板块排行批次不存在: " + batchId);
        }
        return itemMapper.selectByBatchId(batchId).stream().map(this::toItemVO).toList();
    }

    private MarketSectorRankingBatchVO collect(MarketSectorRankingConfigDO config, CollectionWindow window,
                                                LocalDateTime now) {
        String token = UUID.randomUUID().toString();
        int claimed = configMapper.tryClaim(config.getId(), token, now,
                now.minusMinutes(MarketSectorCollectionConstants.CLAIM_STALE_MINUTES));
        if (claimed == 0) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PLAN_RUNNING, "该市场板块排行正在采集");
        }
        try {
            MarketSectorRankingBatchDO existing = batchMapper.selectByBucket(config.getProviderCode(),
                    config.getMarketCode(), window.snapshotType(), window.bucketTime());
            if (existing != null) {
                configMapper.markSuccess(config.getId(), window.snapshotType(), now, window.tradeDate(), token);
                return toBatchVO(existing);
            }
            List<MarketSectorRankVO> ranks = catalogService.getIndustryRank(config.getMarketCode(),
                    "leading-gainer", "single", config.getRankLimit());
            if (ranks.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.MARKET_DATA_EMPTY_RESULT, "板块排行返回空结果");
            }
            MarketSectorRankingBatchDO batch = buildBatch(config, window, now, ranks);
            List<MarketSectorRankingItemDO> items = buildItems(ranks);
            try {
                persistenceManager.persist(batch, items);
            } catch (DuplicateKeyException exception) {
                batch = batchMapper.selectByBucket(config.getProviderCode(), config.getMarketCode(),
                        window.snapshotType(), window.bucketTime());
                if (batch == null) throw exception;
            }
            configMapper.markSuccess(config.getId(), window.snapshotType(), now, window.tradeDate(), token);
            return toBatchVO(batch);
        } catch (RuntimeException exception) {
            Failure failure = classify(exception, config.getConsecutiveFailures());
            configMapper.markFailure(config.getId(), failure.state(), failure.nextRetryAt(now),
                    failure.errorCode(), abbreviate(exception.getMessage()), token);
            throw exception;
        } finally {
            configMapper.releaseClaim(config.getId(), token);
        }
    }

    private MarketSectorRankingBatchDO buildBatch(MarketSectorRankingConfigDO config, CollectionWindow window,
                                                   LocalDateTime now, List<MarketSectorRankVO> ranks) {
        Comparator<MarketSectorRankVO> comparator = Comparator.comparing(MarketSectorRankVO::changeRate,
                Comparator.nullsLast(Comparator.naturalOrder()));
        MarketSectorRankVO leader = ranks.stream().filter(item -> item.changeRate() != null)
                .max(comparator).orElse(ranks.get(0));
        MarketSectorRankVO laggard = ranks.stream().filter(item -> item.changeRate() != null)
                .min(comparator).orElse(ranks.get(ranks.size() - 1));
        return MarketSectorRankingBatchDO.builder()
                .providerCode(config.getProviderCode()).marketCode(config.getMarketCode())
                .tradeDate(window.tradeDate()).snapshotType(window.snapshotType())
                .snapshotBucketTime(window.bucketTime()).snapshotTime(now).itemCount(ranks.size())
                .risingCount(count(ranks, 1)).fallingCount(count(ranks, -1)).flatCount(count(ranks, 0))
                .leaderSectorId(leader.providerSectorId()).leaderSectorName(leader.name())
                .leaderChangeRate(leader.changeRate()).laggardSectorId(laggard.providerSectorId())
                .laggardSectorName(laggard.name()).laggardChangeRate(laggard.changeRate())
                .qualityStatus(MarketSectorCollectionConstants.QUALITY_VALID).build();
    }

    private List<MarketSectorRankingItemDO> buildItems(List<MarketSectorRankVO> ranks) {
        return java.util.stream.IntStream.range(0, ranks.size()).mapToObj(index -> {
            MarketSectorRankVO item = ranks.get(index);
            return MarketSectorRankingItemDO.builder().rankNo(index + 1)
                    .providerSectorId(item.providerSectorId()).sectorName(item.name())
                    .changeRate(item.changeRate()).leadingName(item.leadingName())
                    .leadingSymbol(item.leadingSymbol()).leadingChangeRate(item.leadingChangeRate())
                    .indicatorName(item.indicatorName()).indicatorValue(item.indicatorValue()).build();
        }).toList();
    }

    private int count(List<MarketSectorRankVO> ranks, int direction) {
        return (int) ranks.stream().map(MarketSectorRankVO::changeRate).filter(value -> value != null)
                .filter(value -> Integer.signum(value.compareTo(BigDecimal.ZERO)) == direction).count();
    }

    private Failure classify(RuntimeException exception, Integer failures) {
        ErrorCodeEnum code = exception instanceof BusinessException business ? business.getErrorCode()
                : ErrorCodeEnum.INTERNAL_ERROR;
        return switch (code) {
            case MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED ->
                    new Failure(MarketSectorCollectionConstants.STATE_BLOCKED_AUTH, code.getCode(), null);
            case MARKET_DATA_PROVIDER_PERMISSION_DENIED ->
                    new Failure(MarketSectorCollectionConstants.STATE_BLOCKED_PERMISSION, code.getCode(), null);
            case MARKET_SECTOR_PROVIDER_UNAVAILABLE ->
                    new Failure(MarketSectorCollectionConstants.STATE_BLOCKED_CONFIG, code.getCode(), null);
            default -> new Failure(MarketSectorCollectionConstants.STATE_BACKOFF, code.getCode(),
                    backoffMinutes(failures == null ? 0 : failures));
        };
    }

    private int backoffMinutes(int failures) {
        return switch (Math.min(failures, 4)) { case 0 -> 1; case 1 -> 2; case 2 -> 5; case 3 -> 10; default -> 30; };
    }

    private MarketSectorRankingConfigDO requireConfig(String market) {
        MarketSectorRankingConfigDO config = configMapper.selectByMarket(market);
        if (config == null) throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "板块排行配置不存在: " + market);
        return config;
    }

    private String normalizeMarket(String market) {
        String normalized = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        if (!MarketSectorCollectionConstants.SUPPORTED_MARKETS.contains(normalized)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "market 必须为 CN/HK/US");
        }
        return normalized;
    }

    private String normalizeSnapshotType(String type) {
        if (type == null || type.isBlank()) return null;
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!List.of(MarketSectorCollectionConstants.SNAPSHOT_INTRADAY,
                MarketSectorCollectionConstants.SNAPSHOT_CLOSE,
                MarketSectorCollectionConstants.SNAPSHOT_MANUAL).contains(normalized)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "snapshotType 不合法");
        }
        return normalized;
    }

    private String abbreviate(String message) {
        if (message == null) return "板块排行采集失败";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private MarketSectorRankingConfigVO toConfigVO(MarketSectorRankingConfigDO item) {
        return new MarketSectorRankingConfigVO(item.getId(), item.getProviderCode(), item.getMarketCode(),
                item.getEnabled(), item.getIntradayIntervalMinutes(), item.getCloseSnapshotEnabled(), item.getRankLimit(),
                item.getExecutionState(), item.getLastIntradayAt(), item.getLastCloseTradeDate(), item.getLastSuccessAt(),
                item.getNextRetryAt(), item.getConsecutiveFailures(), item.getLastErrorCode(),
                item.getLastErrorMessage(), item.getUpdatedAt());
    }

    private MarketSectorRankingBatchVO toBatchVO(MarketSectorRankingBatchDO item) {
        return new MarketSectorRankingBatchVO(item.getId(), item.getProviderCode(), item.getMarketCode(),
                item.getTradeDate(), item.getSnapshotType(), item.getSnapshotBucketTime(), item.getSnapshotTime(),
                item.getItemCount(), item.getRisingCount(), item.getFallingCount(), item.getFlatCount(),
                item.getLeaderSectorId(), item.getLeaderSectorName(), item.getLeaderChangeRate(),
                item.getLaggardSectorId(), item.getLaggardSectorName(), item.getLaggardChangeRate(), item.getQualityStatus());
    }

    private MarketSectorRankingItemVO toItemVO(MarketSectorRankingItemDO item) {
        return new MarketSectorRankingItemVO(item.getId(), item.getBatchId(), item.getRankNo(),
                item.getProviderSectorId(), item.getSectorName(), item.getChangeRate(), item.getLeadingName(),
                item.getLeadingSymbol(), item.getLeadingChangeRate(), item.getIndicatorName(), item.getIndicatorValue());
    }

    private record Failure(String state, String errorCode, Integer backoffMinutes) {
        LocalDateTime nextRetryAt(LocalDateTime now) {
            return backoffMinutes == null ? null : now.plusMinutes(backoffMinutes);
        }
    }
}
