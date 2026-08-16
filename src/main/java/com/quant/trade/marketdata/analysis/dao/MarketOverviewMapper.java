package com.quant.trade.marketdata.analysis.dao;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MR-1A 市场全景只读查询。仅 SELECT（衍生只读边界：不写任何事实表），SQL 全部位于
 * src/main/resources/mapper/MarketOverviewMapper.xml；列名映射依赖 map-underscore-to-camel-case。
 * 数据来源与 MR-0 PoC 相同的已落库事实：stock_daily_bar（data_source=TENCENT_PUBLIC、
 * adjust_type=NONE）、mr0_universe_snapshot（SINA_PUBLIC）、mr0_industry_membership
 * （taxonomy=SINA_INDUSTRY）。本接口不外联 provider、不读取资金流（官方口径资金流 UNAVAILABLE）。
 */
@Mapper
public interface MarketOverviewMapper {

    /**
     * 窗口（含预热）日 K 行。providerCode 固定 TENCENT_PUBLIC；同时覆盖基准与样本股，
     * 由调用方按样本清单过滤。amount 单位元、volume 单位股（MR-0 字典 D6 入库口径）。
     */
    List<DailyBarRow> selectDailyBars(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                      @Param("providerCode") String providerCode);

    /**
     * 证券池快照行（symbol 升序、as_of 倒序，调用方取最新一档）。asOfDate 不设上界：
     * 与 MR-0 PoC 冻结口径一致（分析时点可见最新档快照，时点穿越假设由 limitations 显式声明）。
     */
    List<UniverseSnapshotRow> selectUniverseSnapshots(@Param("providerCode") String providerCode);

    /** 行业成分行（symbol 升序、as_of 倒序，调用方每 symbol 取最新一档）。 */
    List<MembershipRow> selectIndustryMemberships(@Param("taxonomyCode") String taxonomyCode);

    /** 日 K 只读行（单位：amount 元、volume 股）。 */
    @Data
    class DailyBarRow {
        private String canonicalSymbol, dataSource, adjustType;
        private LocalDate tradeDate;
        private BigDecimal openPrice, highPrice, lowPrice, closePrice, amount;
        private Long volume;
        private LocalDateTime fetchedAt;
    }

    /** 证券池快照只读行（circulatingMarketCap 单位元，样本 Top-N 排序键；基准行该列为 null）。 */
    @Data
    class UniverseSnapshotRow {
        private String canonicalSymbol, name, market, providerCode;
        private BigDecimal turnoverRate, circulatingMarketCap;
        private LocalDate asOfDate;
    }

    /** 行业成分只读行（SINA_INDUSTRY 互斥分类，非申万）。 */
    @Data
    class MembershipRow {
        private String industryCode, industryName, canonicalSymbol, providerCode;
        private LocalDate asOfDate;
    }
}
