package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.SecurityDirectorySyncStateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SecurityDirectorySyncStateMapper {
    int upsertByProvider(SecurityDirectorySyncStateDO record);
    SecurityDirectorySyncStateDO selectByProvider(@Param("provider") String provider);
}
