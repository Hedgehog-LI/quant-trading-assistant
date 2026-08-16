package com.quant.trade.marketdata.foundation.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 任务证券范围 Mapper（批量插入，uk task+symbol 幂等）。 */
@Mapper
public interface MdfBackfillTaskSymbolMapper {

    int insertBatch(@Param("taskId") Long taskId, @Param("symbols") List<String> symbols);

    List<String> selectByTask(@Param("taskId") Long taskId);

    long countByTask(@Param("taskId") Long taskId);
}
