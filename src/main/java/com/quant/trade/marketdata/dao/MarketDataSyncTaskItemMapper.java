package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketDataSyncTaskItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 同步任务明细 Mapper。 */
@Mapper
public interface MarketDataSyncTaskItemMapper {
    int insert(MarketDataSyncTaskItemDO record);
    int batchInsert(@Param("list") List<MarketDataSyncTaskItemDO> list);
    int updateById(MarketDataSyncTaskItemDO record);
    MarketDataSyncTaskItemDO selectById(@Param("id") Long id);
    List<MarketDataSyncTaskItemDO> selectByTaskId(@Param("taskId") Long taskId,
                                                  @Param("status") String status,
                                                  @Param("limit") int limit, @Param("offset") int offset);
    long countByTaskId(@Param("taskId") Long taskId, @Param("status") String status);
    List<MarketDataSyncTaskItemDO> selectByPlanId(@Param("planId") Long planId,
                                                  @Param("limit") int limit);
}
