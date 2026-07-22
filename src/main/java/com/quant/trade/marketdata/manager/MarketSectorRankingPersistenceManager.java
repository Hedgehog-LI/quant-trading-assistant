package com.quant.trade.marketdata.manager;

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
    private final MarketSectorRankingBatchMapper batchMapper;
    private final MarketSectorRankingItemMapper itemMapper;

    @Transactional
    public void persist(MarketSectorRankingBatchDO batch, List<MarketSectorRankingItemDO> items) {
        batchMapper.insert(batch);
        items.forEach(item -> item.setBatchId(batch.getId()));
        itemMapper.insertBatch(items);
    }
}
