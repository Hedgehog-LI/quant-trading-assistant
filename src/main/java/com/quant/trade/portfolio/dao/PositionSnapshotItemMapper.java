package com.quant.trade.portfolio.dao;

import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 持仓快照明细表 MyBatis Mapper。
 */
@Mapper
public interface PositionSnapshotItemMapper {

    /** 批量插入某次快照的明细。 */
    int insertBatch(@Param("items") List<PositionSnapshotItemDO> items);

    /** 查询某次快照的全部明细。 */
    List<PositionSnapshotItemDO> selectBySnapshotId(@Param("snapshotId") Long snapshotId);

    /** 更新草稿时删除旧明细，随后在同一事务中写入新明细。 */
    int deleteBySnapshotId(@Param("snapshotId") Long snapshotId);
}
