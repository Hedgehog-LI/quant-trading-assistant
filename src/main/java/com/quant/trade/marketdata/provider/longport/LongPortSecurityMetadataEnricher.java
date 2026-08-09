package com.quant.trade.marketdata.provider.longport;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.provider.LongPortMarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderSecurityInfo;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher;

/**
 * LongPort Static Info 元数据补全实现（D3-03）。
 * <p>
 * 复用 {@link LongPortMarketDataProvider#getSecurityStaticInfo(String)} 已封装的 canonical↔longPort
 * 映射与 {@link MarketDataProvider.ProviderSecurityInfo}，不直接反射 SDK。
 * <p>
 * 本实现负责三道防线：
 * <ol>
 *   <li>证券身份一致性：provider 返回的 {@code canonicalSymbol}（由 provider symbol 经
 *       {@code LongPortSymbolMapper.fromLongPort} 转换回系统代码）必须与请求一致，否则抛
 *       {@link ErrorCodeEnum#SECURITY_VERIFICATION_FAILED}，禁止返回或持久化其他证券的静态信息。</li>
 *   <li>数据规范化：LongPort 返回的字符串必须 trim，null/空字符串/纯空白统一视为 null，
 *       禁止把空白写入数据库。</li>
 *   <li>provider 无数据语义：provider 返回 null 时返回 {@code enriched=false} +
 *       {@code reason=PROVIDER_NOT_FOUND}（不是 OK），由 service 呈现为不落库。</li>
 * </ol>
 * provider 抛出的 {@link BusinessException}（鉴权失败/超时/权限不足等）由本实现透传，
 * 不吞掉、不包装，错误码与 {@code GlobalExceptionHandler} 已有映射一致。
 */
public class LongPortSecurityMetadataEnricher implements SecurityMetadataEnricher {

    private final MarketDataProvider provider;

    public LongPortSecurityMetadataEnricher(MarketDataProvider provider) {
        this.provider = provider;
    }

    @Override
    public boolean isEnabled() {
        return provider.isConfigured();
    }

    @Override
    public String getProviderCode() {
        return MarketDataConstants.PROVIDER_CODE_LONGPORT;
    }

    @Override
    public EnrichResult enrich(EnrichRequest request) {
        // provider 未配置时由 provider.ensureConfigured() 抛 BUSINESS_RULE_VIOLATION（透传，不吞）。
        // 正常路径下 provider 会映射 canonical↔longPort 并返回 ProviderSecurityInfo（null=未找到）。
        ProviderSecurityInfo info = provider.getSecurityStaticInfo(request.canonicalSymbol());
        if (info == null) {
            return new EnrichResult(request.canonicalSymbol(), false, getProviderCode(),
                    null, null, EnrichReason.PROVIDER_NOT_FOUND);
        }

        // 证券身份一致性：provider 返回的证券必须与请求一致，否则拒绝返回/持久化。
        String returnedCanonical = info.canonicalSymbol();
        if (returnedCanonical == null || !returnedCanonical.equals(request.canonicalSymbol())) {
            throw new BusinessException(ErrorCodeEnum.SECURITY_VERIFICATION_FAILED,
                    "provider 返回的证券与请求不一致: 请求 " + request.canonicalSymbol()
                            + ", provider 返回 " + (returnedCanonical == null ? "<null>" : returnedCanonical));
        }

        EnrichFields fields = new EnrichFields(
                normalizeBlank(info.nameCn()), normalizeBlank(info.nameHk()), normalizeBlank(info.nameEn()),
                normalizeBlank(info.exchange()), normalizeBlank(info.currency()));
        return new EnrichResult(request.canonicalSymbol(), true, getProviderCode(),
                fields, info.lotSize(), EnrichReason.OK);
    }

    /** LongPort 字符串规范化：trim；null/空字符串/纯空白统一视为 null。 */
    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
