package com.quant.trade.review.dao;

import com.quant.trade.review.model.ReviewNoteDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 盘后复盘 MyBatis Mapper。
 */
@Mapper
public interface ReviewNoteMapper {

    int insert(ReviewNoteDO record);

    int updateById(ReviewNoteDO record);

    ReviewNoteDO selectById(@Param("id") Long id);

    List<ReviewNoteDO> selectByFilter(@Param("date") LocalDate date,
                                      @Param("symbol") String symbol);

    /**
     * 查询全部复盘记录。
     * <p>
     * 用于工作台与复盘一致性回算：扫描所有 linked_journal_ids 解析当前被引用的交易记录。
     * 个人项目数据量可控，全表扫描足够。
     *
     * @return 全部复盘记录
     */
    List<ReviewNoteDO> selectAll();

    long countByReviewDate(@Param("reviewDate") LocalDate reviewDate);

    /**
     * 根据主键物理删除。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
