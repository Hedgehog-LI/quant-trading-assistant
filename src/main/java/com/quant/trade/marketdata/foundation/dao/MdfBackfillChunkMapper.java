package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfBackfillChunkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 回补分片 Mapper（断点=按 chunk 状态续跑）。 */
@Mapper
public interface MdfBackfillChunkMapper {

    int insertBatch(@Param("list") List<MdfBackfillChunkDO> chunks);

    List<MdfBackfillChunkDO> selectByTaskId(@Param("taskId") Long taskId);

    long countByTaskAndStatus(@Param("taskId") Long taskId, @Param("status") String status);

    int updateById(MdfBackfillChunkDO chunk);

    /** 重试准备：FAILED→PENDING（attempts 保留、错误清空）。返回重置分片数。 */
    int resetFailedToPending(@Param("taskId") Long taskId, @Param("now") LocalDateTime now);
}
