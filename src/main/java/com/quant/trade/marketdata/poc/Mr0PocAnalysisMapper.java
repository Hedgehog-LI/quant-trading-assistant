package com.quant.trade.marketdata.poc;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MR-0 PoC 只读分析查询（SLICE-03，AC-05/AC-06）。仅 SELECT；写入 SQL 全在 SLICE-02 Mr0PocMapper.xml。
 * 本接口 6 条查询 SQL 位于 src/main/resources/mapper/Mr0PocAnalysisMapper.xml（AC-01 迁移，
 * statement id 与方法名一一对应）；列名映射依赖 map-underscore-to-camel-case。
 * providerCode/asOfDate 传 null = 不设上界（质量检查跨源核对、抓取日快照聚合历史窗口）。
 * countMarketCalendar 为质量族 4 要求的本地事实读取，超出冻结 5 项读取清单，已在自检回执披露。
 */
@Mapper
public interface Mr0PocAnalysisMapper {

    /** 窗口日 K 行（含 data_source/adjust_type，供分析与质量引擎共用）。 */
    List<BarRow> selectDailyBars(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                 @Param("providerCode") String providerCode);

    /**
     * 证券池快照行（symbol 升序、as_of 倒序，调用方取最新一档）。asOfDate=null 不设上界：真实 PoC 用
     * 抓取日快照聚合历史窗口，须可见且由 TIME_POINT_LOOKAHEAD 族显式标记（冻结测试 M4 场景）。
     * circulating_market_cap 供 CR-3 样本派生（最新档快照流通市值 Top-N）。
     */
    List<UniverseRow> selectUniverseSnapshots(@Param("asOfDate") LocalDate asOfDate,
                                              @Param("providerCode") String providerCode);

    /**
     * 行业成分行（symbol 升序、as_of 倒序，调用方每 symbol 取最新一档）。asOfDate=null 同上：
     * 当前成分聚合历史=PoC 显式假设（时点穿越由质量族标记，非静默）。
     */
    List<MembershipRow> selectIndustryMemberships(@Param("asOfDate") LocalDate asOfDate,
                                                  @Param("taxonomyCode") String taxonomyCode);

    /** 窗口个股日资金流行行（净额元，SINA_PUBLIC 事实）。 */
    List<MoneyFlowRow> selectMoneyFlows(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                        @Param("providerCode") String providerCode);

    /** 窗口内按 data_source 分组的日 K 行数（质量检查 COVERAGE/DUPLICATES 用）。 */
    List<ProviderCountRow> countDailyBarsByProvider(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** market_calendar 指定市场行数（质量检查 STALENESS 第 4 族的本地空表发现）。 */
    long countMarketCalendar(@Param("marketCode") String marketCode);

    /** 日 K 只读行（单位：amount 元、volume 股，字典总则 D6）。 */
    @Data
    class BarRow {
        private String canonicalSymbol, dataSource, adjustType;
        private LocalDate tradeDate;
        private BigDecimal openPrice, highPrice, lowPrice, closePrice, amount;
        private Long volume;
        private LocalDateTime fetchedAt;
    }

    /** 证券池快照只读行（circulatingMarketCap 单位元，CR-3 Top-N 排序键；基准行该列为 null）。 */
    @Data
    class UniverseRow { private String canonicalSymbol, name, market, providerCode; private BigDecimal turnoverRate, circulatingMarketCap; private LocalDate asOfDate; }

    /** 行业成分只读行。 */
    @Data
    class MembershipRow { private String industryCode, industryName, canonicalSymbol; private LocalDate asOfDate; private LocalDateTime fetchedAt; }

    /** 个股日资金流只读行（industry_net_inflow 即新浪 cate_na 口径）。 */
    @Data
    class MoneyFlowRow { private String canonicalSymbol, providerCode, industryCode; private LocalDate tradeDate; private BigDecimal mainNetInflow, industryNetInflow; private LocalDateTime fetchedAt; }

    /** data_source 分组计数行。 */
    @Data
    class ProviderCountRow { private String dataSource; private long barCount; }
}
