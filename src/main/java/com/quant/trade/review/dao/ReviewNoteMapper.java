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

    long countByReviewDate(@Param("reviewDate") LocalDate reviewDate);

    /**
     * 根据主键物理删除。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
