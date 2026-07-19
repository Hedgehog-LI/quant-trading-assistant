package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketDataSyncTaskDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MarketDataSyncTaskMapper {
    int insert(MarketDataSyncTaskDO record);
    int updateById(MarketDataSyncTaskDO record);
    MarketDataSyncTaskDO selectById(@Param("id") Long id);
    MarketDataSyncTaskDO selectByIdempotencyKey(@Param("key") String key);
    List<MarketDataSyncTaskDO> selectByFilter(@Param("status") String status, @Param("provider") String provider,
                                              @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("status") String status, @Param("provider") String provider);
    int deleteById(@Param("id") Long id);
    MarketDataSyncTaskDO selectLatestByScope(@Param("provider") String provider, @Param("taskType") String taskType, @Param("scopeJson") String scopeJson);
    int markFailedIfNonTerminal(@Param("id") Long id, @Param("errorCode") String errorCode,
                                @Param("errorSummaryJson") String errorSummaryJson,
                                @Param("finishedAt") java.time.LocalDateTime finishedAt);
}
