package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 数据集版本 Mapper。 */
@Mapper
public interface MdfDatasetVersionMapper {

    int insert(MdfDatasetVersionDO version);

    MdfDatasetVersionDO selectById(@Param("id") Long id);

    List<MdfDatasetVersionDO> selectByDatasetId(@Param("datasetId") Long datasetId);

    /** 版本序号最大值（version_code 形如 v1/v2；空返回 0）。 */
    int selectMaxVersionSeq(@Param("datasetId") Long datasetId);

    /** 状态流转（含 qualified_at/released_at 按需置位）。 */
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("qualifiedAt") java.time.LocalDateTime qualifiedAt,
                     @Param("releasedAt") java.time.LocalDateTime releasedAt,
                     @Param("sourceNote") String sourceNote);

    int updateRowCount(@Param("id") Long id, @Param("rowCount") Long rowCount);
}
