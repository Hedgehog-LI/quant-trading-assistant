package com.quant.trade.marketdata.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortDailyBar;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortQuote;

import java.time.LocalDate;
import java.util.List;

/** LongPort 只读行情 provider，负责系统代码、SDK 代码和领域模型转换。 */
public class LongPortMarketDataProvider implements MarketDataProvider {

    private static final String SDK_ADJUST_TYPE_NONE = "NoAdjust";
    private static final String SDK_ADJUST_TYPE_FORWARD = "ForwardAdjust";

    private final LongPortProperties properties;
    private final LongPortSymbolMapper symbolMapper;
    private final LongPortQuoteClient quoteClient;

    public LongPortMarketDataProvider(LongPortProperties properties, LongPortSymbolMapper symbolMapper,
                                      LongPortQuoteClient quoteClient) {
        this.properties = properties;
        this.symbolMapper = symbolMapper;
        this.quoteClient = quoteClient;
    }

    @Override
    public String getProviderCode() {
        return MarketDataConstants.PROVIDER_CODE_LONGPORT;
    }

    @Override
    public boolean isConfigured() {
        return properties.isEnabled() && quoteClient.isSdkAvailable() && quoteClient.hasCredentials();
    }

    @Override
    public ProviderHealthStatus healthCheck() {
        if (!properties.isEnabled()) {
            return new ProviderHealthStatus(false, false,
                    MarketDataConstants.LONGPORT_PROVIDER_DISABLED_MESSAGE, null);
        }
        if (!quoteClient.isSdkAvailable() || !quoteClient.hasCredentials()) {
            return new ProviderHealthStatus(false, false, quoteClient.unavailableReason(), null);
        }
        return quoteClient.healthCheck();
    }

    @Override
    public List<ProviderQuote> getLatestQuotes(List<String> canonicalSymbols) {
        ensureConfigured();
        if (canonicalSymbols == null || canonicalSymbols.isEmpty()) {
            return List.of();
        }
        if (canonicalSymbols.size() > MarketDataConstants.LONGPORT_MAX_QUOTE_SYMBOLS) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "单次 LongPort 最新行情查询最多支持 "
                            + MarketDataConstants.LONGPORT_MAX_QUOTE_SYMBOLS + " 个标的");
        }

        List<String> longPortSymbols = canonicalSymbols.stream()
                .map(this::toLongPortSymbol)
                .toList();

        return quoteClient.getLatestQuotes(longPortSymbols).stream()
                .map(this::toProviderQuote)
                .toList();
    }

    @Override
    public List<ProviderDailyBar> getDailyBars(String canonicalSymbol, LocalDate startDate, LocalDate endDate,
                                               String adjustType) {
        ensureConfigured();
        String longPortSymbol = toLongPortSymbol(canonicalSymbol);
        String sdkAdjustType = toSdkAdjustType(adjustType);
        String systemAdjustType = normalizeAdjustType(adjustType);
        return quoteClient.getDailyBars(longPortSymbol, startDate, endDate, sdkAdjustType, systemAdjustType)
                .stream()
                .map(bar -> toProviderDailyBar(canonicalSymbol, bar))
                .toList();
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, healthCheck().lastError());
        }
    }

    private String toLongPortSymbol(String canonicalSymbol) {
        if (canonicalSymbol == null || canonicalSymbol.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, "证券代码不能为空");
        }
        String longPortSymbol = symbolMapper.toLongPort(canonicalSymbol);
        if (longPortSymbol == null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL,
                    "证券代码格式不合法: " + canonicalSymbol);
        }
        return longPortSymbol;
    }

    private String normalizeAdjustType(String adjustType) {
        return adjustType == null || adjustType.isBlank() ? "NONE" : adjustType.trim().toUpperCase();
    }

    private String toSdkAdjustType(String adjustType) {
        String normalized = normalizeAdjustType(adjustType);
        if ("NONE".equals(normalized)) {
            return SDK_ADJUST_TYPE_NONE;
        }
        if ("QF".equals(normalized)) {
            return SDK_ADJUST_TYPE_FORWARD;
        }
        if ("HF".equals(normalized)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "LongPort Java SDK 当前未提供后复权枚举，HF 暂不支持");
        }
        throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                "复权类型不合法: " + adjustType);
    }

    private ProviderQuote toProviderQuote(LongPortQuote quote) {
        String canonicalSymbol = symbolMapper.fromLongPort(quote.longPortSymbol());
        if (canonicalSymbol == null) {
            canonicalSymbol = quote.longPortSymbol();
        }
        return new ProviderQuote(canonicalSymbol, quote.quoteTime(), quote.currentPrice(),
                quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.preClosePrice(),
                quote.volume(), quote.amount(), quote.tradeStatus());
    }

    private ProviderDailyBar toProviderDailyBar(String canonicalSymbol, LongPortDailyBar bar) {
        return new ProviderDailyBar(canonicalSymbol, bar.tradeDate(), bar.adjustType(),
                bar.openPrice(), bar.highPrice(), bar.lowPrice(), bar.closePrice(),
                bar.volume(), bar.amount());
    }
}
