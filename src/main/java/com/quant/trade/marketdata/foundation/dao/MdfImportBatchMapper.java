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

    List<MdfImportBatchDO> selectList(@Param("importKind") String importKind,
                                      @Param("offset") int offset, @Param("limit") int limit);
}
