package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfQualityResultDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 数据质量检查结果 Mapper。 */
@Mapper
public interface MdfQualityResultMapper {

    /** 删除旧结果后重写（版本内 check_code 唯一）。 */
    int deleteByVersion(@Param("datasetVersionId") Long datasetVersionId);

    int insertBatch(@Param("list") List<MdfQualityResultDO> results);

    List<MdfQualityResultDO> selectByVersion(@Param("datasetVersionId") Long datasetVersionId);

    long countFailByVersion(@Param("datasetVersionId") Long datasetVersionId);
}
