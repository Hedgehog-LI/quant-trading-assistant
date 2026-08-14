package com.quant.trade.marketdata.asset.service;

import com.quant.trade.marketdata.asset.manager.MarketDataAssetSeriesManager;
import com.quant.trade.marketdata.asset.manager.MarketDataAssetCatalogManager;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetCatalogItemVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetAvailabilityVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetRelatedTasksVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetSeriesVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.quant.trade.marketdata.vo.PageResultVO;

/** P1.9-A 行情资产只读查询应用服务。 */
@Service
@RequiredArgsConstructor
public class MarketDataAssetQueryService {

    private final MarketDataAssetSeriesManager seriesManager;
    private final MarketDataAssetCatalogManager catalogManager;

    public PageResultVO<MarketDataAssetCatalogItemVO> listAssets(String market, String keyword, int page, int size) {
        return catalogManager.list(market, keyword, page, size);
    }

    public MarketDataAssetAvailabilityVO getAvailability(String canonicalSymbol) {
        return seriesManager.buildAvailability(canonicalSymbol);
    }

    public MarketDataAssetSeriesVO getSeries(String canonicalSymbol, String interval, String from, String to,
                                             String adjustType, String dataSource) {
        return seriesManager.buildSeries(canonicalSymbol, interval, from, to, adjustType, dataSource);
    }

    public MarketDataAssetRelatedTasksVO getRelatedTasks(String canonicalSymbol, String interval, String from,
                                                         String to, int page, int size) {
        return seriesManager.buildRelatedTasks(canonicalSymbol, interval, from, to, page, size);
    }
}
