package com.quant.trade.marketdata.poc;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MR-0 PoC 只读分析查询（SLICE-03，AC-05/AC-06）。仅 SELECT；写入 SQL 全在 SLICE-02 Mr0PocMapper.xml。
 * 已记录约定偏离（父级裁决，移交 CODE_REVIEWER）：项目惯例"SQL 写 XML"，但本切片冻结允许路径不含
 * mapper XML（锚定不可变），故只读查询用 MyBatis 注解 @Select；列名映射依赖 map-underscore-to-camel-case。
 * providerCode/asOfDate 传 null = 不设上界（质量检查跨源核对、抓取日快照聚合历史窗口）。
 * countMarketCalendar 为质量族 4 要求的本地事实读取，超出冻结 5 项读取清单，已在自检回执披露。
 */
@Mapper
public interface Mr0PocAnalysisMapper {

    /** 窗口日 K 行（含 data_source/adjust_type，供分析与质量引擎共用）。 */
    @Select("""
            <script>
            SELECT canonical_symbol, trade_date, open_price, high_price, low_price, close_price,
                   volume, amount, data_source, adjust_type, fetched_at
            FROM stock_daily_bar
            WHERE trade_date &gt;= #{start} AND trade_date &lt;= #{end}
            <if test="providerCode != null">AND data_source = #{providerCode}</if>
            ORDER BY canonical_symbol, trade_date
            </script>
            """)
    List<BarRow> selectDailyBars(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                 @Param("providerCode") String providerCode);

    /**
     * 证券池快照行（symbol 升序、as_of 倒序，调用方取最新一档）。asOfDate=null 不设上界：真实 PoC 用
     * 抓取日快照聚合历史窗口，须可见且由 TIME_POINT_LOOKAHEAD 族显式标记（冻结测试 M4 场景）。
     * circulating_market_cap 供 CR-3 样本派生（最新档快照流通市值 Top-N）。
     */
    @Select("""
            <script>
            SELECT canonical_symbol, name, market, turnover_rate, circulating_market_cap, provider_code, as_of_date
            FROM mr0_universe_snapshot
            WHERE provider_code = #{providerCode}
            <if test="asOfDate != null">AND as_of_date &lt;= #{asOfDate}</if>
            ORDER BY as_of_date DESC, canonical_symbol
            </script>
            """)
    List<UniverseRow> selectUniverseSnapshots(@Param("asOfDate") LocalDate asOfDate,
                                              @Param("providerCode") String providerCode);

    /**
     * 行业成分行（symbol 升序、as_of 倒序，调用方每 symbol 取最新一档）。asOfDate=null 同上：
     * 当前成分聚合历史=PoC 显式假设（时点穿越由质量族标记，非静默）。
     */
    @Select("""
            <script>
            SELECT industry_code, industry_name, canonical_symbol, as_of_date, fetched_at
            FROM mr0_industry_membership
            WHERE taxonomy_code = #{taxonomyCode}
            <if test="asOfDate != null">AND as_of_date &lt;= #{asOfDate}</if>
            ORDER BY canonical_symbol, as_of_date DESC
            </script>
            """)
    List<MembershipRow> selectIndustryMemberships(@Param("asOfDate") LocalDate asOfDate,
                                                  @Param("taxonomyCode") String taxonomyCode);

    /** 窗口个股日资金流行（净额元，SINA_PUBLIC 事实）。非 script 注解 SQL 不做 XML 实体转义。 */
    @Select("""
            SELECT canonical_symbol, trade_date, provider_code, main_net_inflow, industry_code,
                   industry_net_inflow, fetched_at
            FROM mr0_stock_money_flow_daily
            WHERE provider_code = #{providerCode} AND trade_date >= #{start} AND trade_date <= #{end}
            ORDER BY canonical_symbol, trade_date
            """)
    List<MoneyFlowRow> selectMoneyFlows(@Param("start") LocalDate start, @Param("end") LocalDate end,
                                        @Param("providerCode") String providerCode);

    /** 窗口内按 data_source 分组的日 K 行数（质量检查 COVERAGE/DUPLICATES 用）。 */
    @Select("""
            SELECT data_source, COUNT(1) AS bar_count FROM stock_daily_bar
            WHERE trade_date >= #{start} AND trade_date <= #{end}
            GROUP BY data_source ORDER BY data_source
            """)
    List<ProviderCountRow> countDailyBarsByProvider(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** market_calendar 指定市场行数（质量检查 STALENESS 第 4 族的本地空表发现）。 */
    @Select("SELECT COUNT(1) FROM market_calendar WHERE market_code = #{marketCode}")
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
