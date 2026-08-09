package com.quant.trade.marketdata.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;

/**
 * 元数据补全 disabled 兜底实现（D3-03）。
 * <p>
 * 应用未启用 longport provider 时由 {@code MarketDataConfig} 通过
 * {@code @ConditionalOnMissingBean(SecurityMetadataEnricher.class)} 装配本类，
 * 保证应用可正常启动且 D1 本地搜索/导入/详情不受影响。
 * <p>
 * 与 longport/目录 provider 的 disabled 兜底同构：{@link #isEnabled()} 恒为 false，
 * 调用 {@link #enrich(EnrichRequest)} 直接抛 {@link BusinessException}（BUSINESS_RULE_VIOLATION），
 * 经 {@code GlobalExceptionHandler} 映射为 HTTP 400，绝不返回错误结果也不泄露凭据。
 */
public class DisabledSecurityMetadataEnricher implements SecurityMetadataEnricher {

    /** disabled 兜底的固定 provider code（≤16 字符，与目录 disabled 兜底风格一致）。 */
    public static final String PROVIDER_CODE_DISABLED = "DISABLED";

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE_DISABLED;
    }

    @Override
    public EnrichResult enrich(EnrichRequest request) {
        throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                "证券元数据补全 provider 未启用");
    }
}
