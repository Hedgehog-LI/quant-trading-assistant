package com.quant.trade.portfolio.dao;

import com.quant.trade.portfolio.model.PositionSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 持仓快照主表 MyBatis Mapper。
 */
@Mapper
public interface PositionSnapshotMapper {

    /** 插入快照主记录。 */
    int insert(PositionSnapshotDO record);

    /**
     * 更新草稿内容。SQL 同时校验当前状态仍为 DRAFT，防止并发状态变更后被覆盖。
     */
    int updateDraft(PositionSnapshotDO record);

    /**
     * 从允许的当前状态原子流转到目标状态。
     */
    int updateStatus(@Param("id") Long id,
                     @Param("targetStatus") String targetStatus,
                     @Param("currentStatuses") List<String> currentStatuses);

    /** 根据主键查询。 */
    PositionSnapshotDO selectById(@Param("id") Long id);

    /** 按日期、状态和来源查询历史快照。 */
    List<PositionSnapshotDO> selectByFilter(@Param("fromDate") LocalDate fromDate,
                                            @Param("toDate") LocalDate toDate,
                                            @Param("status") String status,
                                            @Param("sourceType") String sourceType,
                                            @Param("includeCanceled") boolean includeCanceled);

    /** 查询最新一条已确认快照。 */
    PositionSnapshotDO selectLatestConfirmed();
}
