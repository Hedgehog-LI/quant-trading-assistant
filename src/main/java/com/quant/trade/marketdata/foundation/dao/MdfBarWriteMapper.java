package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.model.StockDailyBarDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 数据底座日 K 写入 Mapper（写既有 stock_daily_bar 事实表，ODKU 幂等，先例 Mr0PocMapper.xml）。 */
@Mapper
public interface MdfBarWriteMapper {

    int upsertBatch(@Param("list") List<StockDailyBarDO> bars);
}
