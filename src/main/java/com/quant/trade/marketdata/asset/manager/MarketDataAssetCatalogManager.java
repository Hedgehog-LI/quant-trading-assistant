package com.quant.trade.marketdata.asset.manager;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.asset.dao.MarketDataAssetCatalogMapper;
import com.quant.trade.marketdata.asset.model.MarketDataAssetCatalogDO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetCatalogItemVO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.util.MarketDataAssetTimeFormatter;
import com.quant.trade.marketdata.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 已入库行情资产目录查询与 VO 组装。 */
@Component
@RequiredArgsConstructor
public class MarketDataAssetCatalogManager {

    private static final int MAX_PAGE_SIZE = 100;

    private final MarketDataAssetCatalogMapper catalogMapper;
    private final MarketDataAssetSecurityMeta securityMeta;

    public PageResultVO<MarketDataAssetCatalogItemVO> list(String market, String keyword, int page, int size) {
        validatePaging(page, size);
        int offset = (page - 1) * size;
        String normalizedMarket = market == null ? null : market.trim().toUpperCase();
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        var items = catalogMapper.selectByFilter(normalizedMarket, normalizedKeyword, size, offset)
                .stream().map(this::toVO).toList();
        long total = catalogMapper.countByFilter(normalizedMarket, normalizedKeyword);
        return PageResultVO.of(items, total, page, size);
    }

    private MarketDataAssetCatalogItemVO toVO(MarketDataAssetCatalogDO row) {
        StockBasicDO security = StockBasicDO.builder()
                .canonicalSymbol(row.getCanonicalSymbol())
                .name(row.getName())
                .nameCn(row.getNameCn())
                .nameHk(row.getNameHk())
                .nameEn(row.getNameEn())
                .market(row.getMarket())
                .currency(row.getCurrency())
                .build();
        LocalDateTime latestFetchedAt = latest(row.getLatestDailyFetchedAt(), row.getLatestMinuteFetchedAt());
        return new MarketDataAssetCatalogItemVO(
                securityMeta.toSecurityVO(security),
                row.getDailyBarCount(), row.getMinuteBarCount(), row.getMinuteIntervalCount(),
                text(row.getFirstDailyDate()), text(row.getLastDailyDate()),
                MarketDataAssetTimeFormatter.formatStoredTime(row.getFirstMinuteTime()),
                MarketDataAssetTimeFormatter.formatStoredTime(row.getLastMinuteTime()),
                MarketDataAssetTimeFormatter.formatStoredTime(latestFetchedAt));
    }

    private void validatePaging(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "page 必须 >= 1，size 必须在 1.." + MAX_PAGE_SIZE);
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }
}
