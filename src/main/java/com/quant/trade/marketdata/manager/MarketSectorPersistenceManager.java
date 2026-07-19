package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.dao.MarketSectorMemberSnapshotMapper;
import com.quant.trade.marketdata.dao.MarketSectorSnapshotMapper;
import com.quant.trade.marketdata.dao.MarketSectorWatchMapper;
import com.quant.trade.marketdata.model.MarketSectorMemberSnapshotDO;
import com.quant.trade.marketdata.model.MarketSectorSnapshotDO;
import com.quant.trade.marketdata.model.MarketSectorWatchDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 保证行业关注、聚合快照和成分快照原子落库。 */
@Component
@RequiredArgsConstructor
public class MarketSectorPersistenceManager {
    private final MarketSectorWatchMapper watchMapper;
    private final MarketSectorSnapshotMapper snapshotMapper;
    private final MarketSectorMemberSnapshotMapper memberMapper;

    @Transactional
    public void createWithSnapshot(MarketSectorWatchDO watch, MarketSectorSnapshotDO snapshot,
                                   List<MarketSectorMemberSnapshotDO> members) {
        watchMapper.insert(watch);
        snapshot.setWatchId(watch.getId());
        persistSnapshot(snapshot, members);
        watchMapper.updateRefreshResult(watch.getId(), snapshot.getSnapshotTime(), null);
    }

    @Transactional
    public void appendSnapshot(MarketSectorSnapshotDO snapshot, List<MarketSectorMemberSnapshotDO> members) {
        persistSnapshot(snapshot, members);
        watchMapper.updateRefreshResult(snapshot.getWatchId(), snapshot.getSnapshotTime(), null);
    }

    @Transactional
    public void deleteWatch(Long id) {
        watchMapper.deleteById(id);
    }

    private void persistSnapshot(MarketSectorSnapshotDO snapshot, List<MarketSectorMemberSnapshotDO> members) {
        snapshotMapper.insert(snapshot);
        if (!members.isEmpty()) {
            members.forEach(member -> member.setSnapshotId(snapshot.getId()));
            memberMapper.insertBatch(members);
        }
    }
}
