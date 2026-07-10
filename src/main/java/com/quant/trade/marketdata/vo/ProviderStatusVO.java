package com.quant.trade.marketdata.vo;

import java.time.LocalDateTime;

/** Provider 状态响应 VO（不暴露密钥）。 */
public record ProviderStatusVO(
    String providerCode, boolean configured, boolean reachable,
    String lastError, LocalDateTime lastSuccessAt
) {}
