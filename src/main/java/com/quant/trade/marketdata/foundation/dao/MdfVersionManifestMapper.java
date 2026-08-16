package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfManifestDriftPairDO;
import com.quant.trade.marketdata.foundation.model.MdfManifestRowHashDO;
import com.quant.trade.marketdata.foundation.model.MdfSymbolBarStatDO;
import com.quant.trade.marketdata.foundation.model.MdfVersionManifestDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

import java.time.LocalDate;
import java.util.List;

/**
 * 版本血缘 manifest Mapper：批量幂等写入（行哈希 Java 侧冻结）、流式内容哈希、漂移校验输入、
 * manifest 域聚合（质量检查只检本版本归属行，多 Provider 合法共存）。
 */
@Mapper
public interface MdfVersionManifestMapper {

    /** 批量幂等写入 manifest（ODKU；uk bar_id/业务键双保险）。 */
    int insertBatch(@Param("list") List<MdfVersionManifestDO> rows);

    long countByVersion(@Param("datasetVersionId") Long datasetVersionId);

    /** 流式读取有序（symbol,trade_date,row_hash）供内容哈希（ResultHandler 避免整表驻内存）。 */
    void streamOrderedRowHashes(@Param("datasetVersionId") Long datasetVersionId,
                                ResultHandler<MdfManifestRowHashDO> handler);

    /** 漂移校验输入：冻结哈希 + bar 当前内容（含 bar 缺失 LEFT NULL）；Java 侧重算比对。 */
    void streamDriftPairs(@Param("datasetVersionId") Long datasetVersionId,
                          ResultHandler<MdfManifestDriftPairDO> handler);

    /** manifest 域内 source/adjust 混入（join bar 比对版本声明口径）行数。 */
    long countForeignRows(@Param("datasetVersionId") Long datasetVersionId,
                          @Param("expectedDataSource") String expectedDataSource,
                          @Param("expectedAdjustType") String expectedAdjustType);

    /** manifest 域日历日覆盖（去重日期数）。 */
    long countDistinctDates(@Param("datasetVersionId") Long datasetVersionId);

    /** manifest 域某日有 bar 的证券数（首/末边界覆盖）。 */
    long countSymbolsOnDate(@Param("datasetVersionId") Long datasetVersionId,
                            @Param("tradeDate") LocalDate tradeDate);

    /** manifest 域证券×行数统计（覆盖水位）。 */
    List<MdfSymbolBarStatDO> selectSymbolStats(@Param("datasetVersionId") Long datasetVersionId);

    // ---------------- R1 §四/§五：manifest 域（版本归属行）质量聚合，多 Provider 合法共存 ----------------

    /** manifest 业务键重复（uk 防线破坏检测）。 */
    long countDuplicatedKeys(@Param("datasetVersionId") Long datasetVersionId);

    long countOhlcViolations(@Param("datasetVersionId") Long datasetVersionId);

    long countUnitAnomalies(@Param("datasetVersionId") Long datasetVersionId);

    /** 非交易日行（周末；calendarDays>0 时含日历外日期）。 */
    long countNonTradingDayRows(@Param("datasetVersionId") Long datasetVersionId,
                                @Param("marketCode") String marketCode,
                                @Param("calendarDays") long calendarDays);

    java.time.LocalDateTime selectMaxFetchedAt(@Param("datasetVersionId") Long datasetVersionId);
}
