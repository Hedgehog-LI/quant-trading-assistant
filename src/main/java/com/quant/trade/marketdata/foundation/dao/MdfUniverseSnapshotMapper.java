package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfUniverseSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 历史股票池快照 Mapper（幂等 ODKU，先例 Mr0PocMapper.xml）。 */
@Mapper
public interface MdfUniverseSnapshotMapper {

    int upsertBatch(@Param("list") List<MdfUniverseSnapshotDO> rows);

    /** 最新快照日（无数据返回 NULL）。 */
    LocalDate selectLatestAsOfDate();

    List<String> selectSymbolsByAsOf(@Param("asOfDate") LocalDate asOfDate);

    long countByAsOf(@Param("asOfDate") LocalDate asOfDate);
}
