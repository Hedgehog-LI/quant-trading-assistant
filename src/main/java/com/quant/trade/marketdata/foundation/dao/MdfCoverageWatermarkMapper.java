package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfCoverageWatermarkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 数据覆盖水位 Mapper。 */
@Mapper
public interface MdfCoverageWatermarkMapper {

    int upsertBatch(@Param("list") List<MdfCoverageWatermarkDO> rows);

    List<MdfCoverageWatermarkDO> selectByVersion(@Param("datasetVersionId") Long datasetVersionId);
}
