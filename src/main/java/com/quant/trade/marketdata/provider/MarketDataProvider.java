package com.quant.trade.marketdata.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行情 Provider 只读抽象。
 * <p>
 * 禁止在此接口或任何实现中添加下单、改单、撤单、账户资金、真实持仓、订单、成交等交易能力。
 * 本接口只允许：最新行情查询、历史日 K 查询、健康检查。
 */
public interface MarketDataProvider {

    /** Provider 唯一标识，如 LONGPORT。 */
    String getProviderCode();

    /** 是否已配置凭据。未配置时使用 DisabledMarketDataProvider 兜底。 */
    boolean isConfigured();

    /** 只读健康检查，不暴露密钥。 */
    ProviderHealthStatus healthCheck();

    /**
     * 获取最新行情（只读）。
     *
     * @param canonicalSymbols 本系统统一代码列表
     * @return 行情快照列表
     */
    List<ProviderQuote> getLatestQuotes(List<String> canonicalSymbols);

    /**
     * 获取历史日 K（只读）。
     *
     * @param canonicalSymbol 本系统统一代码
     * @param startDate       起始日期
     * @param endDate         截止日期
     * @param adjustType      复权类型 NONE/QF/HF
     * @return 日 K 列表
     */
    List<ProviderDailyBar> getDailyBars(String canonicalSymbol, LocalDate startDate, LocalDate endDate, String adjustType);

    // ==================== Provider 领域模型（嵌套 record） ====================

    /** Provider 健康状态（不含密钥）。 */
    record ProviderHealthStatus(boolean configured, boolean reachable, String lastError, LocalDateTime lastSuccessAt) {}

    /** Provider 返回的最新行情。 */
    record ProviderQuote(String canonicalSymbol, LocalDateTime quoteTime, BigDecimal currentPrice,
                        BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
                        BigDecimal preClosePrice, Long volume, BigDecimal amount, String tradeStatus) {}

    /** Provider 返回的历史日 K。 */
    record ProviderDailyBar(String canonicalSymbol, LocalDate tradeDate, String adjustType,
                           BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
                           Long volume, BigDecimal amount) {}
}
