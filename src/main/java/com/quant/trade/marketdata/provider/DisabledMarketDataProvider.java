package com.quant.trade.marketdata.provider;

import com.quant.trade.marketdata.constant.MarketDataConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

/** 未配置 LongPort 时使用的降级 provider（不请求外部）。 */
@Component
@ConditionalOnProperty(prefix = "qta.market-data.longport", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledMarketDataProvider implements MarketDataProvider {
    @Override public String getProviderCode() { return MarketDataConstants.PROVIDER_CODE_LONGPORT; }
    @Override public boolean isConfigured() { return false; }
    @Override public ProviderHealthStatus healthCheck() {
        return new ProviderHealthStatus(false, false, MarketDataConstants.LONGPORT_PROVIDER_DISABLED_MESSAGE, null);
    }
    @Override public ProviderSecurityInfo getSecurityStaticInfo(String canonicalSymbol) {
        throw new UnsupportedOperationException("LongPort provider 未配置，无法验证证券");
    }
    @Override public List<ProviderQuote> getLatestQuotes(List<String> canonicalSymbols) {
        throw new UnsupportedOperationException("LongPort provider 未配置，无法获取行情");
    }

    @Override
    public List<ProviderDailyBar> getDailyBars(String canonicalSymbol, LocalDate startDate, LocalDate endDate, String adjustType) {
        throw new UnsupportedOperationException("LongPort provider 未配置，无法获取历史日 K");
    }

    @Override
    public List<ProviderMinuteBar> getMinuteBars(String canonicalSymbol, LocalDate startDate, LocalDate endDate,
                                                 String intervalType, String adjustType) {
        throw new UnsupportedOperationException("LongPort provider 未配置，无法获取历史分钟 K");
    }
}
