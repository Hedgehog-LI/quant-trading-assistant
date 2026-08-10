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
    /** 查询指定 task 的全部 item（不分页，用于 reconcile 全量统计）。 */
    List<MarketDataSyncTaskItemDO> selectAllByTaskId(@Param("taskId") Long taskId);
    List<MarketDataSyncTaskItemDO> selectByPlanId(@Param("planId") Long planId,
                                                  @Param("limit") int limit);
    int markFailedIfNonTerminal(@Param("taskId") Long taskId, @Param("errorCode") String errorCode,
                                @Param("errorMessage") String errorMessage,
                                @Param("finishedAt") java.time.LocalDateTime finishedAt);
    /** 查询某证券的采集记录（按开始时间倒序，用于 related-tasks 只读展示）。 */
    List<MarketDataSyncTaskItemDO> selectBySymbol(@Param("canonicalSymbol") String canonicalSymbol,
                                                  @Param("limit") int limit, @Param("offset") int offset);
    long countBySymbol(@Param("canonicalSymbol") String canonicalSymbol);
}
