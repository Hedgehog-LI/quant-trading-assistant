package com.quant.trade.journal.dao;

import com.quant.trade.journal.model.TradeJournalDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 交易记录 MyBatis Mapper。
 */
@Mapper
public interface TradeJournalMapper {

    int insert(TradeJournalDO record);

    int updateById(TradeJournalDO record);

    TradeJournalDO selectById(@Param("id") Long id);

    List<TradeJournalDO> selectByFilter(@Param("date") LocalDate date,
                                        @Param("symbol") String symbol,
                                        @Param("reviewStatus") String reviewStatus);

    List<TradeJournalDO> selectByIds(@Param("ids") Collection<Long> ids);

    long countByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    long countByReviewStatus(@Param("reviewStatus") String reviewStatus);

    /**
     * 全量交易流水，按交易时间正序排列（FIFO 配对所需）。
     *
     * @param fromDate 起始日期（可空）
     * @param toDate   截止日期（可空）
     * @return 按时间正序的流水
     */
    List<TradeJournalDO> selectAllOrdered(@Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate);

    /**
     * 单股票交易流水，按交易时间正序排列。
     *
     * @param symbol 股票代码
     * @return 按时间正序的流水
     */
    List<TradeJournalDO> selectBySymbolOrdered(@Param("symbol") String symbol);

    /**
     * 批量更新复核状态。
     *
     * @param ids          ID 列表
     * @param reviewStatus 目标状态
     * @return 影响行数
     */
    int batchUpdateReviewStatus(@Param("ids") Collection<Long> ids,
                                @Param("reviewStatus") String reviewStatus);

    /**
     * 根据主键物理删除。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
