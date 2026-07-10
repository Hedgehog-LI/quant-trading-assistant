package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.StockQuoteSnapshotDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StockQuoteSnapshotMapper {
    int upsert(StockQuoteSnapshotDO record);
    StockQuoteSnapshotDO selectByUniqueKey(@Param("canonicalSymbol") String s, @Param("dataSource") String ds, @Param("quoteTime") LocalDateTime qt);
    List<StockQuoteSnapshotDO> selectByFilter(@Param("canonicalSymbol") String s, @Param("dataSource") String ds,
                                               @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("canonicalSymbol") String s, @Param("dataSource") String ds);
}
