package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.provider.MarketSectorProvider;
import com.quant.trade.marketdata.vo.MarketSectorPeerVO;
import com.quant.trade.marketdata.vo.MarketSectorRankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 编排市场行业排行和层级查询，不写入自定义板块表。 */
@Service
@RequiredArgsConstructor
public class MarketSectorCatalogService {

    private static final Set<String> MARKETS = Set.of("CN", "HK", "US");
    private static final Set<String> INDICATORS = Set.of("leading-gainer", "today-trend", "popularity",
            "market-cap", "revenue", "revenue-growth", "net-profit", "net-profit-growth");
    private static final Set<String> SORT_TYPES = Set.of("single", "multi");
    private static final int MAX_LIMIT = 100;

    private final MarketSectorProvider provider;

    public List<MarketSectorRankVO> getIndustryRank(String market, String indicator,
                                                     String sortType, int limit) {
        String normalizedMarket = normalizeMarket(market);
        String normalizedIndicator = normalizeChoice(indicator, INDICATORS, "行业排行指标不合法");
        String normalizedSortType = normalizeChoice(sortType, SORT_TYPES, "行业排行排序方式不合法");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalid("limit 必须在 1 到 100 之间");
        }
        ensureProviderReady();
        return provider.getIndustryRank(normalizedMarket, normalizedIndicator, normalizedSortType, limit).stream()
                .map(item -> new MarketSectorRankVO(item.market(), item.name(), item.providerSectorId(),
                        item.changeRate(), item.leadingName(), item.leadingSymbol(), item.leadingChangeRate(),
                        item.indicatorName(), item.indicatorValue(), item.providerCode()))
                .toList();
    }

    public MarketSectorPeerVO getIndustryPeers(String market, String counterId) {
        String normalizedMarket = normalizeMarket(market);
        String normalizedCounterId = counterId == null ? "" : counterId.trim();
        String marketSegment = switch (normalizedMarket) {
            case "CN" -> "(?:SH|SZ|BJ|CN)";
            case "HK" -> "HK";
            case "US" -> "US";
            default -> normalizedMarket;
        };
        if (!normalizedCounterId.matches("BK/" + marketSegment + "/[A-Za-z0-9_-]+")) {
            throw invalid("providerSectorId 必须符合 BK/市场/ID 格式且与 market 一致");
        }
        ensureProviderReady();
        var peer = provider.getIndustryPeers(normalizedMarket, normalizedCounterId);
        if (peer == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "未查询到行业层级信息");
        }
        return new MarketSectorPeerVO(peer.market(), peer.topName(), peer.name(), peer.providerSectorId(),
                peer.stockCount(), peer.changeRate(), peer.yearToDateChangeRate(), peer.hasChildren(),
                peer.providerCode());
    }

    private String normalizeMarket(String market) {
        String normalized = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        if (!MARKETS.contains(normalized)) {
            throw invalid("market 必须为 CN/HK/US");
        }
        return normalized;
    }

    private String normalizeChoice(String value, Set<String> allowed, String message) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalid(message);
        }
        return normalized;
    }

    private void ensureProviderReady() {
        if (!provider.isConfigured()) {
            throw new BusinessException(ErrorCodeEnum.MARKET_SECTOR_PROVIDER_UNAVAILABLE,
                    "板块数据源未配置，仍可使用自定义分组和 ETF 行情采集");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCodeEnum.PARAM_ERROR, message);
    }
}
