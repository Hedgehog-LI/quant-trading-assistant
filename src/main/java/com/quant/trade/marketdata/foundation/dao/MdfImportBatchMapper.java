package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfImportBatchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** CSV/快照导入批次 Mapper（import_kind+file_hash 唯一=同内容幂等）。 */
@Mapper
public interface MdfImportBatchMapper {

    int insert(MdfImportBatchDO batch);

    MdfImportBatchDO selectById(@Param("id") Long id);

    MdfImportBatchDO selectByKindAndHash(@Param("importKind") String importKind, @Param("fileHash") String fileHash);

    /** R1：批次先建（拿 id 供 manifest 血缘）后回填计数/错误报告。 */
    int updateCounts(@Param("id") Long id, @Param("insertedCount") int insertedCount,
                     @Param("updatedCount") int updatedCount, @Param("skippedCount") int skippedCount,
                     @Param("rejectedCount") int rejectedCount,
                     @Param("errorReportJson") String errorReportJson);

    List<MdfImportBatchDO> selectList(@Param("importKind") String importKind,
                                      @Param("offset") int offset, @Param("limit") int limit);
}
