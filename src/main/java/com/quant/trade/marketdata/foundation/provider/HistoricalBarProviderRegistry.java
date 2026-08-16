package com.quant.trade.marketdata.foundation.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/** 按 providerCode 解析 HistoricalBarProvider（Spring 注入全部实现）。 */
@Component
public class HistoricalBarProviderRegistry {

    private final List<HistoricalBarProvider> providers;

    public HistoricalBarProviderRegistry(List<HistoricalBarProvider> providers) {
        this.providers = providers;
    }

    public HistoricalBarProvider require(String providerCode) {
        return providers.stream()
                .filter(provider -> provider.providerCode().equals(providerCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.MARKET_DATA_PLAN_INVALID,
                        "无可用历史回补 Provider: " + providerCode));
    }
}
