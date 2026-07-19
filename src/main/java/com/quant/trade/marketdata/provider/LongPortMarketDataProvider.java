package com.quant.trade.marketdata.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortDailyBar;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortQuote;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortMinuteBar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    public ProviderSecurityInfo getSecurityStaticInfo(String canonicalSymbol) {
        ensureConfigured();
        String longPortSymbol = toLongPortSymbol(canonicalSymbol);
        var info = quoteClient.getSecurityStaticInfo(longPortSymbol);
        if (info == null) {
            return null;
        }
        String normalized = symbolMapper.fromLongPort(info.longPortSymbol());
        return new ProviderSecurityInfo(normalized, info.longPortSymbol(), info.nameCn(), info.nameHk(),
                info.nameEn(), info.exchange(), info.currency(), info.lotSize());
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
        String normalizedCanonicalSymbol = symbolMapper.fromLongPort(longPortSymbol);
        String sdkAdjustType = toSdkAdjustType(adjustType);
        String systemAdjustType = normalizeAdjustType(adjustType);
        return quoteClient.getDailyBars(longPortSymbol, startDate, endDate, sdkAdjustType, systemAdjustType)
                .stream()
                .map(bar -> toProviderDailyBar(normalizedCanonicalSymbol, bar))
                .toList();
    }

    @Override
    public List<ProviderMinuteBar> getMinuteBars(String canonicalSymbol, LocalDate startDate, LocalDate endDate,
                                                 String intervalType, String adjustType) {
        ensureConfigured();
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "分钟 K 日期范围不合法");
        }
        String longPortSymbol = toLongPortSymbol(canonicalSymbol);
        String normalizedCanonicalSymbol = symbolMapper.fromLongPort(longPortSymbol);
        String normalizedInterval = normalizeIntervalType(intervalType);
        String sdkPeriod = toSdkPeriod(normalizedInterval);
        String sdkAdjustType = toSdkAdjustType(adjustType);
        String systemAdjustType = normalizeAdjustType(adjustType);

        // 官方日期区间接口单次最多 1000 根。按 A 股每日最大 bar 数保守切段，避免静默丢失区间前部。
        int chunkDays = switch (normalizedInterval) {
            case "1M" -> 4;
            case "5M" -> 20;
            case "15M" -> 62;
            case "30M" -> 125;
            case "60M" -> 250;
            default -> throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "分钟 K 粒度不合法: " + intervalType);
        };
        LinkedHashMap<LocalDateTime, ProviderMinuteBar> deduplicated = new LinkedHashMap<>();
        for (LocalDate chunkStart = startDate; !chunkStart.isAfter(endDate); chunkStart = chunkStart.plusDays(chunkDays)) {
            LocalDate chunkEnd = chunkStart.plusDays(chunkDays - 1L);
            if (chunkEnd.isAfter(endDate)) chunkEnd = endDate;
            List<LongPortMinuteBar> bars = quoteClient.getMinuteBars(longPortSymbol, chunkStart, chunkEnd,
                    sdkPeriod, sdkAdjustType, normalizedInterval, systemAdjustType);
            for (LongPortMinuteBar bar : bars) {
                ProviderMinuteBar converted = new ProviderMinuteBar(normalizedCanonicalSymbol, bar.barStartTime(),
                        normalizedInterval, systemAdjustType, bar.openPrice(), bar.highPrice(), bar.lowPrice(),
                        bar.closePrice(), bar.volume(), bar.amount());
                deduplicated.put(converted.barStartTime(), converted);
            }
        }
        return new ArrayList<>(deduplicated.values()).stream()
                .sorted(Comparator.comparing(ProviderMinuteBar::barStartTime))
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

    private String normalizeIntervalType(String intervalType) {
        return intervalType == null ? "" : intervalType.trim().toUpperCase();
    }

    private String toSdkPeriod(String intervalType) {
        return switch (intervalType) {
            case "1M" -> "Min_1";
            case "5M" -> "Min_5";
            case "15M" -> "Min_15";
            case "30M" -> "Min_30";
            case "60M" -> "Min_60";
            default -> throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "LongPort 不支持的分钟 K 粒度: " + intervalType);
        };
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
