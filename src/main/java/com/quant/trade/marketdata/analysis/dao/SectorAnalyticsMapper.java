package com.quant.trade.marketdata.analysis.dao;

import com.quant.trade.marketdata.analysis.model.MarketResearchRowDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsCalculationRunDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsPublicationBatchDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsSourceBatchDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsSourceItemDO;
import com.quant.trade.marketdata.analysis.model.SectorRelativeStrengthResultDO;
import com.quant.trade.marketdata.analysis.model.SectorRotationPersistenceResultDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 板块分析运行、衍生结果和只读模型 Mapper。 */
@Mapper
public interface SectorAnalyticsMapper {

    List<SectorAnalyticsSourceBatchDO> selectCloseBatches(
            @Param("providerCode") String providerCode, @Param("marketCode") String marketCode,
            @Param("asOfDate") LocalDate asOfDate, @Param("limit") int limit);

    List<SectorAnalyticsSourceItemDO> selectSourceItems(@Param("batchIds") List<Long> batchIds);

    List<LocalDate> selectTradingDates(@Param("marketCode") String marketCode,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate);

    SectorAnalyticsCalculationRunDO selectRunByIdentity(
            @Param("formulaCode") String formulaCode, @Param("formulaVersion") String formulaVersion,
            @Param("parameterHash") String parameterHash,
            @Param("sourceManifestHash") String sourceManifestHash);

    int insertCalculationRun(SectorAnalyticsCalculationRunDO run);

    int completeCalculationRun(@Param("id") Long id, @Param("qualityStatus") String qualityStatus,
                               @Param("reasonCodes") String reasonCodes, @Param("sampleSize") int sampleSize);

    int insertRelativeStrength(@Param("records") List<SectorRelativeStrengthResultDO> records);

    int insertPersistence(@Param("records") List<SectorRotationPersistenceResultDO> records);

    SectorAnalyticsPublicationBatchDO selectPublicationByIdentity(
            @Param("providerCode") String providerCode, @Param("marketCode") String marketCode,
            @Param("asOfDate") LocalDate asOfDate, @Param("windowDays") int windowDays,
            @Param("momentumWindowDays") int momentumWindowDays,
            @Param("formulaVersion") String formulaVersion, @Param("parameterHash") String parameterHash,
            @Param("sourceManifestGroupHash") String sourceManifestGroupHash);

    int insertPublication(SectorAnalyticsPublicationBatchDO batch);

    int insertPublicationMember(@Param("publicationBatchId") Long publicationBatchId,
                                @Param("calculationRunId") Long calculationRunId,
                                @Param("formulaCode") String formulaCode);

    SectorAnalyticsPublicationBatchDO selectLatestPublication(
            @Param("marketCode") String marketCode, @Param("windowDays") int windowDays,
            @Param("momentumWindowDays") int momentumWindowDays);

    List<MarketResearchRowDO> selectResearchRows(@Param("publicationBatchId") Long publicationBatchId);

    List<MarketResearchRowDO> selectRankingHistoryRows(
            @Param("marketCode") String marketCode, @Param("windowDays") int windowDays,
            @Param("days") int days);

    List<MarketResearchRowDO> selectSectorHistory(
            @Param("marketCode") String marketCode, @Param("sectorId") Long sectorId,
            @Param("windowDays") int windowDays, @Param("days") int days);
}
