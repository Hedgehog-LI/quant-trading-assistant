package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dto.VerifySecurityDTO;
import com.quant.trade.marketdata.manager.SecurityCodeManager;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderQuote;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderSecurityInfo;
import com.quant.trade.marketdata.vo.SecurityVerificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 编排证券静态信息和一次最新报价查询；整个流程只读且不进入数据库事务。 */
@Service
@RequiredArgsConstructor
public class SecurityVerificationService {

    private final MarketDataProvider provider;
    private final SecurityCodeManager securityCodeManager;

    public SecurityVerificationVO verify(VerifySecurityDTO request) {
        String canonicalSymbol = securityCodeManager.toCanonicalSymbol(request.market(), request.code());
        if (!provider.isConfigured()) {
            String reason = provider.healthCheck().lastError();
            return result(canonicalSymbol, null, request.market(), "PROVIDER_UNAVAILABLE", null,
                    reason == null ? "行情 provider 未配置" : reason);
        }

        ProviderSecurityInfo info;
        try {
            info = provider.getSecurityStaticInfo(canonicalSymbol);
        } catch (BusinessException exception) {
            return result(canonicalSymbol, null, request.market(), providerStatus(exception), null,
                    exception.getMessage());
        }
        if (info == null) {
            return result(canonicalSymbol, null, request.market(), "INVALID_SYMBOL", null,
                    "未查询到该市场的证券静态信息");
        }

        ProviderQuote quote = null;
        String status = "VERIFIED_NO_QUOTE";
        String message = "证券身份已验证，当前没有可用报价";
        try {
            List<ProviderQuote> quotes = provider.getLatestQuotes(List.of(canonicalSymbol));
            if (!quotes.isEmpty()) {
                quote = quotes.get(0);
                status = "VERIFIED_QUOTE_AVAILABLE";
                message = null;
            }
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCodeEnum.MARKET_DATA_PROVIDER_PERMISSION_DENIED) {
                status = "NO_PERMISSION";
            }
            message = exception.getMessage();
        }
        return result(canonicalSymbol, info, request.market(), status, quote, message);
    }

    private SecurityVerificationVO result(String canonical, ProviderSecurityInfo info, String market,
                                          String status, ProviderQuote quote, String message) {
        return new SecurityVerificationVO(canonical, info == null ? null : info.providerSymbol(),
                displayName(info, market), market.trim().toUpperCase(), info == null ? null : info.exchange(),
                info == null ? null : info.currency(), info == null ? null : info.lotSize(), status,
                quote != null, quote == null ? null : quote.currentPrice(), quote == null ? null : quote.quoteTime(),
                quote == null ? null : quote.tradeStatus(), "UNKNOWN", provider.getProviderCode(), message);
    }

    private String displayName(ProviderSecurityInfo info, String market) {
        if (info == null) return null;
        String preferred = "US".equalsIgnoreCase(market) ? info.nameEn()
                : "HK".equalsIgnoreCase(market) ? first(info.nameHk(), info.nameCn()) : info.nameCn();
        return first(preferred, info.nameEn(), info.nameHk(), info.canonicalSymbol());
    }

    private String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private String providerStatus(BusinessException exception) {
        if (exception.getErrorCode() == ErrorCodeEnum.MARKET_DATA_PROVIDER_PERMISSION_DENIED) return "NO_PERMISSION";
        return "PROVIDER_UNAVAILABLE";
    }
}
