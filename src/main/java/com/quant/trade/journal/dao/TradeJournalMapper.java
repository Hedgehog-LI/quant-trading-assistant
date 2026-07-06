package com.quant.trade.journal.dao;

import com.quant.trade.journal.model.TradeJournalDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * 截止指定时点的全量交易流水（持仓对账使用），按交易时间正序。
     * <p>
     * 纳入规则：
     * <ul>
     *   <li>{@code trade_date < snapshotDate}：全部纳入；</li>
     *   <li>{@code trade_date = snapshotDate} 且 {@code trade_time} 为空：纳入（同日无时间精度，默认纳入）；</li>
     *   <li>{@code trade_date = snapshotDate} 且 {@code trade_time} 非空：仅 {@code trade_time <= snapshotDateTime} 纳入。</li>
     * </ul>
     *
     * @param snapshotDate     快照日期
     * @param snapshotDateTime 快照时点
     * @return 截止时点的流水
     */
    List<TradeJournalDO> selectAllOrderedUpTo(@Param("snapshotDate") LocalDate snapshotDate,
                                              @Param("snapshotDateTime") LocalDateTime snapshotDateTime);

    /**
     * 查询 trade_date <= toDate 且指定复盘状态的交易（用于 Dashboard 历史日期口径）。
     *
     * @param toDate       截止日期（包含）
     * @param reviewStatus 复盘状态
     */
    List<TradeJournalDO> selectByReviewStatusUpTo(@Param("toDate") LocalDate toDate,
                                                  @Param("reviewStatus") String reviewStatus);

    /**
     * 统计 trade_date <= toDate 且指定复盘状态的交易数。
     *
     * @param toDate       截止日期（包含）
     * @param reviewStatus 复盘状态
     */
    long countByReviewStatusUpTo(@Param("toDate") LocalDate toDate,
                                 @Param("reviewStatus") String reviewStatus);

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
