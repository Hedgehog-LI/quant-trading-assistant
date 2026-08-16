package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 数据集定义 Mapper。 */
@Mapper
public interface MdfDatasetMapper {

    int insert(MdfDatasetDO dataset);

    MdfDatasetDO selectByCode(@Param("datasetCode") String datasetCode);

    MdfDatasetDO selectById(@Param("id") Long id);

    List<MdfDatasetDO> selectAll();

    /** 发布事务内切换当前版本指针。 */
    int updateCurrentVersion(@Param("id") Long id, @Param("currentVersionId") Long currentVersionId);
}
