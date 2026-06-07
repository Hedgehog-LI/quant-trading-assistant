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
     * 批量更新复核状态。
     *
     * @param ids          ID 列表
     * @param reviewStatus 目标状态
     * @return 影响行数
     */
    int batchUpdateReviewStatus(@Param("ids") Collection<Long> ids,
                                @Param("reviewStatus") String reviewStatus);
}
