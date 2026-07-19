package com.quant.trade.marketdata.provider.longport;

import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderHealthStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** LongPort SDK 只读 quote client 边界，禁止扩展交易、账户、订单能力。 */
public interface LongPortQuoteClient {

    /** 当前运行时是否能加载官方 LongPort Java SDK。 */
    boolean isSdkAvailable();

    /** 当前运行时是否具备 LongPort legacy API key 凭据。 */
    boolean hasCredentials();

    /** SDK 缺失或凭据缺失时用于展示的安全错误信息。 */
    String unavailableReason();

    /** 只读健康检查。 */
    ProviderHealthStatus healthCheck();

    /** 获取单个证券静态信息，入参和出参 symbol 均使用 LongPort 格式。 */
    LongPortSecurityInfo getSecurityStaticInfo(String longPortSymbol);

    /** 获取最新行情，入参和出参均为 LongPort symbol，如 600519.SH。 */
    List<LongPortQuote> getLatestQuotes(List<String> longPortSymbols);

    /** 获取历史日 K，symbol 为 LongPort symbol，如 600519.SH。 */
    List<LongPortDailyBar> getDailyBars(String longPortSymbol, LocalDate startDate, LocalDate endDate,
                                        String sdkAdjustTypeName, String systemAdjustType);

    /** 获取历史分钟 K，调用方负责按 SDK 的 1000 根上限分段。 */
    List<LongPortMinuteBar> getMinuteBars(String longPortSymbol, LocalDate startDate, LocalDate endDate,
                                          String sdkPeriodName, String sdkAdjustTypeName,
                                          String systemIntervalType, String systemAdjustType);

    /** LongPort 最新行情 DTO。 */
    record LongPortQuote(String longPortSymbol, LocalDateTime quoteTime, BigDecimal currentPrice,
                         BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
                         BigDecimal preClosePrice, Long volume, BigDecimal amount, String tradeStatus) {}

    /** LongPort 证券静态信息 DTO。 */
    record LongPortSecurityInfo(String longPortSymbol, String nameCn, String nameHk, String nameEn,
                                String exchange, String currency, Integer lotSize) {}

    /** LongPort 历史日 K DTO。 */
    record LongPortDailyBar(String longPortSymbol, LocalDate tradeDate, String adjustType,
                            BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
                            BigDecimal closePrice, Long volume, BigDecimal amount) {}

    record LongPortMinuteBar(String longPortSymbol, LocalDateTime barStartTime, String intervalType,
                             String adjustType, BigDecimal openPrice, BigDecimal highPrice,
                             BigDecimal lowPrice, BigDecimal closePrice, Long volume, BigDecimal amount) {}

}
