package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSectorMemberSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 行业成分快照 Mapper。 */
@Mapper
public interface MarketSectorMemberSnapshotMapper {
    int insertBatch(@Param("records") List<MarketSectorMemberSnapshotDO> records);
    List<MarketSectorMemberSnapshotDO> selectBySnapshotId(@Param("snapshotId") Long snapshotId);
}
