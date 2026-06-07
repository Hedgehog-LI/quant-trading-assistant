package com.quant.trade.watchlist.dao;

import com.quant.trade.watchlist.model.WatchlistDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自选股 MyBatis Mapper。
 */
@Mapper
public interface WatchlistMapper {

    /**
     * 插入一条自选股记录。
     *
     * @param record 自选股数据
     * @return 影响行数
     */
    int insert(WatchlistDO record);

    /**
     * 根据主键更新（仅非空字段）。
     *
     * @param record 自选股数据
     * @return 影响行数
     */
    int updateById(WatchlistDO record);

    /**
     * 根据主键查询。
     *
     * @param id 主键
     * @return 自选股记录
     */
    WatchlistDO selectById(@Param("id") Long id);

    /**
     * 根据股票代码查询。
     *
     * @param symbol 股票代码
     * @return 自选股记录
     */
    WatchlistDO selectBySymbol(@Param("symbol") String symbol);

    /**
     * 判断股票代码是否已存在。
     *
     * @param symbol 股票代码
     * @return 存在数量
     */
    int existsBySymbol(@Param("symbol") String symbol);

    /**
     * 按条件筛选自选股列表。
     *
     * @param enabled    启用状态过滤（null 表示不过滤）
     * @param keyword    名称或代码模糊搜索（null 表示不过滤）
     * @param tradeStyle 交易风格过滤（null 表示不过滤）
     * @return 自选股列表
     */
    List<WatchlistDO> selectByFilter(@Param("enabled") Boolean enabled,
                                     @Param("keyword") String keyword,
                                     @Param("tradeStyle") String tradeStyle);

    /**
     * 查询启用且指定关注等级的自选股。
     *
     * @param attentionLevel 关注等级
     * @return 符合条件的自选股列表
     */
    List<WatchlistDO> selectEnabledHighAttention(@Param("attentionLevel") String attentionLevel);

    /**
     * 统计启用的自选股数量。
     *
     * @return 启用数量
     */
    long countEnabled();
}
