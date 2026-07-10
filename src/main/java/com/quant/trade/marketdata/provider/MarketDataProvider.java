package com.quant.trade.marketdata.provider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 行情 Provider 只读抽象。禁止下单/撤单/账户/订单/真实持仓调用。 */
public interface MarketDataProvider {
    String getProviderCode();
    boolean isConfigured();
    ProviderHealthStatus healthCheck();
    List<ProviderQuote> getLatestQuotes(List<String> canonicalSymbols);

    record ProviderHealthStatus(boolean configured, boolean reachable, String lastError, LocalDateTime lastSuccessAt) {}
    record ProviderQuote(String canonicalSymbol, LocalDateTime quoteTime, BigDecimal currentPrice,
                        BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
                        BigDecimal preClosePrice, Long volume, BigDecimal amount, String tradeStatus) {}
}
