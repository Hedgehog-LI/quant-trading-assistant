package com.quant.trade.marketdata.foundation.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据底座快照文件解析器接口（契约 AC-04）。
 *
 * 五类 schema 表头冻结（见实现类）；表头不匹配=整批 400；行级错误收集进 errors
 * （recordNumber/reason/raw）；文件内重复唯一键计 skipped；单位口径=元/股/小数；
 * 日期 YYYY-MM-DD；PIT 区间半开 [from,to)，to 空=至今，文件内重叠行拒绝。
 */
public interface SnapshotFileParser {

    /** 解析结果：合法行（按唯一键去重后）+ skipped + rejected + 错误明细。 */
    record ParsedRows<T>(List<T> rows, int skipped, int rejected, List<Map<String, Object>> errors) {
    }

    record UniverseRow(String canonicalSymbol, String name, String market,
                       BigDecimal totalMarketCap, BigDecimal circulatingMarketCap,
                       BigDecimal turnoverRate, LocalDate asOfDate) {
    }

    record CalendarRow(String marketCode, LocalDate tradeDate, boolean tradingDay) {
    }

    record DailyBarRow(String canonicalSymbol, LocalDate tradeDate,
                       BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                       Long volumeShares, BigDecimal amountYuan) {
    }

    record TaxonomyRow(String taxonomyCode, String taxonomyName, String providerCode, String note) {
    }

    record MembershipRow(String taxonomyCode, String industryCode, String industryName,
                         String canonicalSymbol, LocalDate effectiveFrom, LocalDate effectiveTo) {
    }

    ParsedRows<UniverseRow> parseUniverse(byte[] content);

    ParsedRows<CalendarRow> parseCalendar(byte[] content);

    ParsedRows<DailyBarRow> parseDailyBar(byte[] content);

    ParsedRows<TaxonomyRow> parseTaxonomy(byte[] content);

    ParsedRows<MembershipRow> parseMembershipPit(byte[] content);
}
