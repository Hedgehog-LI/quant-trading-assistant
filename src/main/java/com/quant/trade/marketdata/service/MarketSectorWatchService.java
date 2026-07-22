package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dao.MarketSectorMemberSnapshotMapper;
import com.quant.trade.marketdata.dao.MarketSectorSnapshotMapper;
import com.quant.trade.marketdata.dao.MarketSectorWatchMapper;
import com.quant.trade.marketdata.dto.CreateMarketSectorWatchDTO;
import com.quant.trade.marketdata.dto.UpdateMarketSectorWatchCollectionDTO;
import com.quant.trade.marketdata.constant.MarketSectorCollectionConstants;
import com.quant.trade.marketdata.manager.MarketSectorPersistenceManager;
import com.quant.trade.marketdata.model.MarketSectorMemberSnapshotDO;
import com.quant.trade.marketdata.model.MarketSectorSnapshotDO;
import com.quant.trade.marketdata.model.MarketSectorWatchDO;
import com.quant.trade.marketdata.provider.MarketSectorProvider;
import com.quant.trade.marketdata.provider.MarketSectorProvider.SectorConstituent;
import com.quant.trade.marketdata.provider.MarketSectorProvider.SectorRank;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import com.quant.trade.marketdata.vo.MarketSectorMemberSnapshotVO;
import com.quant.trade.marketdata.vo.MarketSectorSnapshotVO;
import com.quant.trade.marketdata.vo.MarketSectorWatchVO;
import com.quant.trade.marketdata.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 行业关注、手动刷新和历史查询应用服务。 */
@Service
@RequiredArgsConstructor
public class MarketSectorWatchService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final String SNAPSHOT_INDICATOR = "constituent-summary";

    private final MarketSectorWatchMapper watchMapper;
    private final MarketSectorSnapshotMapper snapshotMapper;
    private final MarketSectorMemberSnapshotMapper memberMapper;
    private final MarketSectorCatalogService catalogService;
    private final MarketSectorProvider provider;
    private final MarketSectorPersistenceManager persistenceManager;
    private final Clock marketDataClock;

    public MarketSectorWatchVO create(CreateMarketSectorWatchDTO dto) {
        var peer = catalogService.getIndustryPeers(dto.getMarket(), dto.getProviderSectorId());
        if (watchMapper.selectByProviderSector(peer.providerCode(), peer.providerSectorId()) != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_RESOURCE, "该行业已经关注");
        }
        String trackingSymbol = normalizeOptionalSymbol(dto.getTrackingSymbol());
        MarketSectorWatchDO watch = MarketSectorWatchDO.builder()
                .providerCode(peer.providerCode()).providerSectorId(peer.providerSectorId())
                .marketCode(peer.market()).sectorName(peer.name()).topName(peer.topName())
                .trackingSymbol(trackingSymbol).enabled(true).build();
        SnapshotBundle bundle = fetchSnapshot(null, peer.market(), peer.providerSectorId(), peer.changeRate(),
                peer.yearToDateChangeRate(), null, MarketSectorCollectionConstants.TRIGGER_MANUAL);
        try {
            persistenceManager.createWithSnapshot(watch, bundle.snapshot(), bundle.members());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_RESOURCE, "该行业已经关注");
        }
        return get(watch.getId());
    }

    public List<MarketSectorWatchVO> list(String market) {
        String normalizedMarket = market == null || market.isBlank() ? null : market.trim().toUpperCase();
        if (normalizedMarket != null && !List.of("CN", "HK", "US").contains(normalizedMarket)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "market 必须为 CN/HK/US");
        }
        return watchMapper.selectAll(normalizedMarket).stream().map(this::toWatchVO).toList();
    }

    public MarketSectorWatchVO get(Long id) {
        return toWatchVO(requireWatch(id));
    }

    public MarketSectorWatchVO refresh(Long id) {
        return collect(requireWatch(id), null, false);
    }

    public MarketSectorWatchVO collectAuto(MarketSectorWatchDO watch, LocalDateTime bucketTime) {
        return collect(watch, bucketTime, true);
    }

    public MarketSectorWatchVO updateCollection(Long id, UpdateMarketSectorWatchCollectionDTO dto) {
        requireWatch(id);
        if (!MarketSectorCollectionConstants.WATCH_INTERVAL_MINUTES.contains(dto.collectIntervalMinutes())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "关注板块频率仅支持 5、10、15、30、60 分钟");
        }
        watchMapper.updateCollectionConfig(id, dto.autoCollectEnabled(), dto.collectIntervalMinutes());
        return get(id);
    }

    private MarketSectorWatchVO collect(MarketSectorWatchDO watch, LocalDateTime bucketTime, boolean automatic) {
        if (bucketTime != null && snapshotMapper.selectByWatchAndBucket(watch.getId(), bucketTime) != null) {
            return get(watch.getId());
        }
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        String token = UUID.randomUUID().toString();
        if (watchMapper.tryClaim(watch.getId(), token, now,
                now.minusMinutes(MarketSectorCollectionConstants.CLAIM_STALE_MINUTES)) == 0) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PLAN_RUNNING, "该关注板块正在采集");
        }
        try {
            var peer = catalogService.getIndustryPeers(watch.getMarketCode(), watch.getProviderSectorId());
            SnapshotBundle bundle = fetchSnapshot(watch.getId(), watch.getMarketCode(), watch.getProviderSectorId(),
                    peer.changeRate(), peer.yearToDateChangeRate(), bucketTime,
                    automatic ? MarketSectorCollectionConstants.TRIGGER_AUTO
                            : MarketSectorCollectionConstants.TRIGGER_MANUAL);
            persistenceManager.appendSnapshot(bundle.snapshot(), bundle.members());
            watchMapper.markCollectionSuccess(watch.getId(), bundle.snapshot().getSnapshotTime(), automatic, token);
            return get(watch.getId());
        } catch (RuntimeException exception) {
            CollectionFailure failure = classify(exception, watch.getConsecutiveFailures());
            watchMapper.markCollectionFailure(watch.getId(), failure.state(), failure.nextRetryAt(now),
                    failure.errorCode(), abbreviate(exception.getMessage()), token);
            throw exception;
        } finally {
            watchMapper.releaseClaim(watch.getId(), token);
        }
    }

    public MarketSectorWatchVO setEnabled(Long id, boolean enabled) {
        requireWatch(id);
        watchMapper.updateEnabled(id, enabled);
        return get(id);
    }

    public void delete(Long id) {
        requireWatch(id);
        persistenceManager.deleteWatch(id);
    }

    public PageResultVO<MarketSectorSnapshotVO> snapshots(Long watchId, int page, int size) {
        requireWatch(watchId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<MarketSectorSnapshotVO> items = snapshotMapper.selectByWatchId(watchId, safeSize,
                (safePage - 1) * safeSize).stream().map(this::toSnapshotVO).toList();
        return PageResultVO.of(items, snapshotMapper.countByWatchId(watchId), safePage, safeSize);
    }

    public List<MarketSectorMemberSnapshotVO> members(Long snapshotId) {
        if (snapshotMapper.selectById(snapshotId) == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "行业快照不存在: " + snapshotId);
        }
        return memberMapper.selectBySnapshotId(snapshotId).stream().map(item ->
                new MarketSectorMemberSnapshotVO(item.getId(), item.getSnapshotId(), item.getCanonicalSymbol(),
                        item.getSecurityName(), item.getCurrentPrice(), item.getPreviousClose(), item.getChangeRate(),
                        item.getNetInflow(), item.getTurnoverAmount(), item.getVolume(), item.getTotalShares(),
                        item.getCirculatingShares(), item.getTags(), item.getTradeStatus(), item.getDelayed())).toList();
    }

    private SnapshotBundle fetchSnapshot(Long watchId, String market, String providerSectorId,
                                         BigDecimal changeRate, BigDecimal yearToDateChangeRate,
                                         LocalDateTime bucketTime, String triggerType) {
        var constituents = provider.getIndustryConstituents(providerSectorId);
        SectorRank rank = provider.getIndustryRank(market, "leading-gainer", "single", 100).stream()
                .filter(item -> providerSectorId.equals(item.providerSectorId())).findFirst().orElse(null);
        List<SectorConstituent> stocks = constituents.stocks().stream()
                .filter(item -> item.canonicalSymbol() != null).toList();
        SectorConstituent constituentLeader = stocks.stream()
                .filter(item -> item.changeRate() != null)
                .max(Comparator.comparing(SectorConstituent::changeRate)).orElse(null);
        String leadingName = rank != null ? rank.leadingName()
                : constituentLeader == null ? null : constituentLeader.name();
        String leadingSymbol = rank != null ? rank.leadingSymbol()
                : constituentLeader == null ? null : constituentLeader.canonicalSymbol();
        BigDecimal leadingChangeRate = rank != null ? rank.leadingChangeRate()
                : constituentLeader == null ? null : constituentLeader.changeRate();
        LocalDateTime now = LocalDateTime.ofInstant(marketDataClock.instant(), marketDataClock.getZone());
        int expectedCount = nullSafeCount(constituents.riseCount()) + nullSafeCount(constituents.fallCount())
                + nullSafeCount(constituents.flatCount());
        if (expectedCount <= 0) expectedCount = stocks.size();
        int delayedCount = (int) stocks.stream().filter(item -> Boolean.TRUE.equals(item.delayed())).count();
        int unmappedCount = Math.max(expectedCount - stocks.size(), 0);
        int coverage = expectedCount == 0 ? 100 : stocks.size() * 100 / expectedCount;
        MarketSectorSnapshotDO snapshot = MarketSectorSnapshotDO.builder()
                .watchId(watchId).snapshotTime(now).snapshotBucketTime(bucketTime).triggerType(triggerType)
                .fetchedAt(now).rankIndicator(SNAPSHOT_INDICATOR)
                .changeRate(rank == null ? changeRate : rank.changeRate())
                .yearToDateChangeRate(yearToDateChangeRate)
                .leadingName(leadingName).leadingSymbol(leadingSymbol).leadingChangeRate(leadingChangeRate)
                .constituentCount(stocks.size()).riseCount(countByChange(stocks, 1))
                .fallCount(countByChange(stocks, -1)).flatCount(countByChange(stocks, 0))
                .totalNetInflow(sum(stocks, ValueType.INFLOW))
                .totalTurnoverAmount(sum(stocks, ValueType.TURNOVER))
                .totalVolume(sum(stocks, ValueType.VOLUME)).dataSource(constituents.providerCode())
                .expectedMemberCount(expectedCount).validMemberCount(stocks.size())
                .delayedMemberCount(delayedCount).unmappedMemberCount(unmappedCount)
                .qualityStatus(coverage >= MarketSectorCollectionConstants.MIN_VALID_MEMBER_COVERAGE_PERCENT
                        ? MarketSectorCollectionConstants.QUALITY_VALID
                        : MarketSectorCollectionConstants.QUALITY_SUSPECT).build();
        List<MarketSectorMemberSnapshotDO> members = stocks.stream().map(this::toMemberDO).toList();
        return new SnapshotBundle(snapshot, members);
    }

    private MarketSectorMemberSnapshotDO toMemberDO(SectorConstituent item) {
        return MarketSectorMemberSnapshotDO.builder().canonicalSymbol(item.canonicalSymbol())
                .securityName(item.name()).currentPrice(item.currentPrice()).previousClose(item.previousClose())
                .changeRate(item.changeRate()).netInflow(item.netInflow()).turnoverAmount(item.turnoverAmount())
                .volume(item.volume()).totalShares(item.totalShares()).circulatingShares(item.circulatingShares())
                .tags(item.tags()).tradeStatus(item.tradeStatus()).delayed(item.delayed()).build();
    }

    private BigDecimal sum(List<SectorConstituent> stocks, ValueType type) {
        List<BigDecimal> values = stocks.stream().map(item -> switch (type) {
                    case INFLOW -> item.netInflow();
                    case TURNOVER -> item.turnoverAmount();
                    case VOLUME -> item.volume();
                }).filter(value -> value != null).toList();
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int countByChange(List<SectorConstituent> stocks, int direction) {
        return (int) stocks.stream().filter(item -> item.changeRate() != null)
                .filter(item -> Integer.signum(item.changeRate().compareTo(BigDecimal.ZERO)) == direction).count();
    }

    private int nullSafeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private MarketSectorWatchVO toWatchVO(MarketSectorWatchDO watch) {
        MarketSectorSnapshotDO latest = snapshotMapper.selectLatestByWatchId(watch.getId());
        return new MarketSectorWatchVO(watch.getId(), watch.getProviderCode(), watch.getProviderSectorId(),
                watch.getMarketCode(), watch.getSectorName(), watch.getTopName(), watch.getTrackingSymbol(),
                watch.getEnabled(), watch.getAutoCollectEnabled(), watch.getCollectIntervalMinutes(),
                watch.getLastRefreshedAt(), watch.getLastAutoCollectedAt(), watch.getCollectionState(),
                watch.getNextRetryAt(), watch.getConsecutiveFailures(), watch.getLastErrorCode(),
                watch.getLastError(), watch.getCreatedAt(), watch.getUpdatedAt(),
                latest == null ? null : toSnapshotVO(latest));
    }

    private MarketSectorSnapshotVO toSnapshotVO(MarketSectorSnapshotDO item) {
        return new MarketSectorSnapshotVO(item.getId(), item.getWatchId(), item.getSnapshotTime(),
                item.getSnapshotBucketTime(), item.getTriggerType(), item.getFetchedAt(),
                item.getRankIndicator(), item.getChangeRate(), item.getYearToDateChangeRate(), item.getLeadingName(),
                item.getLeadingSymbol(), item.getLeadingChangeRate(), item.getConstituentCount(), item.getRiseCount(),
                item.getFallCount(), item.getFlatCount(), item.getTotalNetInflow(), item.getTotalTurnoverAmount(),
                item.getTotalVolume(), item.getDataSource(), item.getExpectedMemberCount(),
                item.getValidMemberCount(), item.getDelayedMemberCount(), item.getUnmappedMemberCount(),
                item.getQualityStatus());
    }

    private MarketSectorWatchDO requireWatch(Long id) {
        MarketSectorWatchDO watch = watchMapper.selectById(id);
        if (watch == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "行业关注不存在: " + id);
        }
        return watch;
    }

    private String normalizeOptionalSymbol(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CanonicalSymbolUtils.normalize(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, exception.getMessage());
        }
    }

    private String abbreviate(String message) {
        if (message == null) return "行业刷新失败";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private CollectionFailure classify(RuntimeException exception, Integer failures) {
        ErrorCodeEnum code = exception instanceof BusinessException business ? business.getErrorCode()
                : ErrorCodeEnum.INTERNAL_ERROR;
        return switch (code) {
            case MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED -> new CollectionFailure(
                    MarketSectorCollectionConstants.STATE_BLOCKED_AUTH, code.getCode(), null);
            case MARKET_DATA_PROVIDER_PERMISSION_DENIED -> new CollectionFailure(
                    MarketSectorCollectionConstants.STATE_BLOCKED_PERMISSION, code.getCode(), null);
            case MARKET_SECTOR_PROVIDER_UNAVAILABLE -> new CollectionFailure(
                    MarketSectorCollectionConstants.STATE_BLOCKED_CONFIG, code.getCode(), null);
            default -> new CollectionFailure(MarketSectorCollectionConstants.STATE_BACKOFF, code.getCode(),
                    switch (Math.min(failures == null ? 0 : failures, 4)) {
                        case 0 -> 1; case 1 -> 2; case 2 -> 5; case 3 -> 10; default -> 30;
                    });
        };
    }

    private enum ValueType { INFLOW, TURNOVER, VOLUME }

    private record SnapshotBundle(MarketSectorSnapshotDO snapshot,
                                  List<MarketSectorMemberSnapshotDO> members) {}

    private record CollectionFailure(String state, String errorCode, Integer backoffMinutes) {
        LocalDateTime nextRetryAt(LocalDateTime now) {
            return backoffMinutes == null ? null : now.plusMinutes(backoffMinutes);
        }
    }
}
