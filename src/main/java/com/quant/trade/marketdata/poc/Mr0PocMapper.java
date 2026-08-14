package com.quant.trade.marketdata.poc;

import com.quant.trade.marketdata.model.StockDailyBarDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * MR-0 PoC 写入边界（SQL 见 mapper/Mr0PocMapper.xml）。幂等统一 ON DUPLICATE KEY UPDATE
 * （MySQL 8.4 与 H2 MODE=MySQL 兼容，先例 SyncScopeLockMapper.xml；禁用 MERGE INTO ... KEY 与
 * INSERT IGNORE）。日 K 落既有 stock_daily_bar（uk 含 data_source，不触碰 CSV/LONGPORT 行）。
 * count* 供 inserted/updated 计数（ODKU 返回行数方言不一致）。
 */
@Mapper
public interface Mr0PocMapper {

    int upsertUniverseSnapshotBatch(@Param("list") List<Mr0PocIngestService.UniverseSnapshotRow> rows);

    int upsertIndustryMembershipBatch(@Param("list") List<Mr0PocIngestService.IndustryMembershipRow> rows);

    int upsertMoneyFlowBatch(@Param("list") List<Mr0PocIngestService.MoneyFlowRow> rows);

    int upsertStockDailyBarBatch(@Param("list") List<StockDailyBarDO> rows);

    long countUniverse(@Param("providerCode") String providerCode, @Param("asOfDate") LocalDate asOfDate,
                       @Param("canonicalSymbols") List<String> canonicalSymbols);

    long countIndustryMembership(@Param("taxonomyCode") String taxonomyCode, @Param("asOfDate") LocalDate asOfDate,
                                 @Param("canonicalSymbols") List<String> canonicalSymbols);

    long countMoneyFlow(@Param("providerCode") String providerCode,
                        @Param("canonicalSymbols") List<String> canonicalSymbols,
                        @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    long countStockDailyBar(@Param("canonicalSymbol") String canonicalSymbol, @Param("dataSource") String dataSource,
                            @Param("adjustType") String adjustType,
                            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
