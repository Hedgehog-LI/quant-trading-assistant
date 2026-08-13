package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.analysis.manager.SectorIdentityManager;
import com.quant.trade.marketdata.dao.MarketSectorRankingBatchMapper;
import com.quant.trade.marketdata.dao.MarketSectorRankingItemMapper;
import com.quant.trade.marketdata.model.MarketSectorRankingBatchDO;
import com.quant.trade.marketdata.model.MarketSectorRankingItemDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 原子持久化全市场板块排行批次和明细。 */
@Component
@RequiredArgsConstructor
public class MarketSectorRankingPersistenceManager {
    private static final String TAXONOMY_VERSION = "LONGPORT_INDUSTRY_V1";

    private final MarketSectorRankingBatchMapper batchMapper;
    private final MarketSectorRankingItemMapper itemMapper;
    private final SectorIdentityManager sectorIdentityManager;

    @Transactional
    public void persist(MarketSectorRankingBatchDO batch, List<MarketSectorRankingItemDO> items) {
        items.forEach(item -> item.setSectorIdentityId(sectorIdentityManager.claimIdentity(
                batch.getProviderCode(), batch.getMarketCode(), item.getProviderSectorId(),
                TAXONOMY_VERSION, item.getSectorName(), batch.getTradeDate(), null).getId()));
        batchMapper.insert(batch);
        items.forEach(item -> item.setBatchId(batch.getId()));
        itemMapper.insertBatch(items);
    }
}
