package com.quant.trade.marketdata.analysis.readiness;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 板块分析就绪门禁只读 Mapper。
 *
 * <p>只读 {@code market_sector_ranking_batch}（CLOSE 批次）和 {@code market_calendar}（校验状态），
 * 不写回任何原始事实表。衍生层不 JOIN watch_id。</p>
 */
@Mapper
public interface SectorAnalyticsReadinessMapper {

    /**
     * 查询某市场最新成功的 CLOSE 排行批次（snapshot_type='CLOSE'，quality_status 非 BLOCKED/BACKOFF）。
     * 返回字段：id, trade_date, snapshot_time, item_count, quality_status。
     */
    Map<String, Object> selectLatestCloseBatch(@Param("marketCode") String marketCode);

    /**
     * 查询某市场在 [from, to] 区间内的交易日历校验状态分布。
     * 返回每行：verification_status, day_count。
     * 用于 HK/US 长窗口 fail-closed 判定（INFERRED 占比 > 0 → INSUFFICIENT_RAW）。
     */
    List<Map<String, Object>> selectCalendarVerificationDistribution(@Param("marketCode") String marketCode,
                                                                     @Param("fromDate") LocalDate fromDate,
                                                                     @Param("toDate") LocalDate toDate);

    Integer countVerifiedTradingDays(@Param("marketCode") String marketCode,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate);

    /** 统计某 CLOSE 批次的排行项数（actual_item_count；不从 expected 反填）。 */
    Integer countBatchItems(@Param("batchId") Long batchId);
}
