package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSectorRankingBatchDO;
import com.quant.trade.marketdata.model.MarketSectorRankingItemDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** V15 板块自动采集核心 MyBatis 映射集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketSectorAutomaticCollectionMapperTest {
    @Autowired MarketSectorRankingConfigMapper configMapper;
    @Autowired MarketSectorRankingBatchMapper batchMapper;
    @Autowired MarketSectorRankingItemMapper itemMapper;

    @Test
    void seededConfigCanBeUpdatedAndClaimed() {
        var config = configMapper.selectByMarket("CN");
        assertNotNull(config);
        assertFalse(config.getEnabled());
        config.setEnabled(true);
        config.setIntradayIntervalMinutes(10);
        config.setCloseSnapshotEnabled(true);
        config.setRankLimit(100);
        assertEquals(1, configMapper.updateConfig(config));

        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        assertEquals(1, configMapper.tryClaim(config.getId(), "claim-1", now, now.minusMinutes(10)));
        assertEquals(0, configMapper.tryClaim(config.getId(), "claim-2", now, now.minusMinutes(10)));
        assertEquals(1, configMapper.releaseClaim(config.getId(), "claim-1"));
    }

    @Test
    void rankingBatchAndItemsRoundTrip() {
        LocalDateTime bucket = LocalDateTime.of(2026, 7, 22, 10, 0);
        MarketSectorRankingBatchDO batch = MarketSectorRankingBatchDO.builder()
                .providerCode("LONGPORT").marketCode("CN").tradeDate(LocalDate.of(2026, 7, 22))
                .snapshotType("INTRADAY").snapshotBucketTime(bucket).snapshotTime(bucket.plusSeconds(3))
                .itemCount(1).risingCount(1).fallingCount(0).flatCount(0)
                .leaderSectorId("BK/CN/ONE").leaderSectorName("测试行业")
                .leaderChangeRate(new BigDecimal("0.0123")).qualityStatus("VALID").build();
        batchMapper.insert(batch);
        itemMapper.insertBatch(List.of(MarketSectorRankingItemDO.builder().batchId(batch.getId()).rankNo(1)
                .providerSectorId("BK/CN/ONE").sectorName("测试行业")
                .changeRate(new BigDecimal("0.0123")).build()));

        assertNotNull(batchMapper.selectByBucket("LONGPORT", "CN", "INTRADAY", bucket));
        assertEquals(1, itemMapper.selectByBatchId(batch.getId()).size());
        assertEquals(1, batchMapper.countByFilter("CN", LocalDate.of(2026, 7, 22), "INTRADAY"));
    }
}
