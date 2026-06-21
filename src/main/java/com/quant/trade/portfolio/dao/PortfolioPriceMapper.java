package com.quant.trade.portfolio.dao;

import com.quant.trade.portfolio.model.PortfolioPriceSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 手工当前价快照 MyBatis Mapper。
 */
@Mapper
public interface PortfolioPriceMapper {

    /**
     * 插入一条快照。
     */
    int insert(PortfolioPriceSnapshotDO record);

    /**
     * 根据主键更新（仅非空字段）。
     */
    int updateById(PortfolioPriceSnapshotDO record);

    /**
     * 根据股票代码 + 价格日期查询（upsert 命中判断）。
     */
    PortfolioPriceSnapshotDO selectBySymbolAndDate(@Param("symbol") String symbol,
                                                   @Param("priceDate") LocalDate priceDate);

    /**
     * 取单只股票的最新快照（price_date 最大）。
     */
    PortfolioPriceSnapshotDO selectLatestBySymbol(@Param("symbol") String symbol);

    /**
     * 取所有股票各自的最新快照（每个 symbol 一条最新价）。
     */
    List<PortfolioPriceSnapshotDO> selectLatestBySymbols();

    /**
     * 查询全部快照（按价格日期倒序）。
     */
    List<PortfolioPriceSnapshotDO> selectAll();
}
