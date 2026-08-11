package com.quant.trade.marketdata.asset.manager;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetSecurityVO;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.exception.MarketDataAssetNotFoundException;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * P1.9-A 证券元信息与规范化：加载证券、规范化 canonical symbol、市场/货币/时区推断。
 * <p>
 * 市场代码优先 stock_basic.market，缺失时按 canonical 前缀回退；展示名按名称字段
 * 优先级取首个非空；货币与展示时区按市场默认。
 */
@Component
@RequiredArgsConstructor
public class MarketDataAssetSecurityMeta {

    private final StockBasicMapper stockBasicMapper;

    /** 规范化 canonical symbol，非法格式抛业务异常。 */
    public String normalize(String raw) {
        try {
            return CanonicalSymbolUtils.normalize(raw);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, exception.getMessage());
        }
    }

    public StockBasicDO loadSecurity(String canonicalSymbol) {
        StockBasicDO security = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
        if (security == null) {
            throw new MarketDataAssetNotFoundException(canonicalSymbol);
        }
        return security;
    }

    public MarketDataAssetSecurityVO toSecurityVO(StockBasicDO security) {
        String market = marketOf(security);
        return new MarketDataAssetSecurityVO(
                security.getCanonicalSymbol(),
                displayNameOf(security),
                market,
                currencyOf(security, market),
                timeZoneOf(market));
    }

    /** 市场代码：优先 stock_basic.market，缺失时按 canonical 前缀回退。 */
    public String marketOf(StockBasicDO security) {
        if (security.getMarket() != null && !security.getMarket().isBlank()) {
            return security.getMarket();
        }
        int separator = security.getCanonicalSymbol().indexOf('.');
        return separator > 0 ? security.getCanonicalSymbol().substring(0, separator) : "SH";
    }

    /** 展示名：按名称字段优先级取首个非空，最后回退 canonical symbol。 */
    public String displayNameOf(StockBasicDO security) {
        for (String value : List.of(
                Objects.requireNonNullElse(security.getName(), ""),
                Objects.requireNonNullElse(security.getNameCn(), ""),
                Objects.requireNonNullElse(security.getNameHk(), ""),
                Objects.requireNonNullElse(security.getNameEn(), ""),
                security.getCanonicalSymbol())) {
            if (!value.isBlank()) {
                return value;
            }
        }
        return security.getCanonicalSymbol();
    }

    /** 货币：优先 stock_basic.currency，缺失时按市场默认。 */
    public String currencyOf(StockBasicDO security, String market) {
        if (security.getCurrency() != null && !security.getCurrency().isBlank()) {
            return security.getCurrency();
        }
        return switch (market) {
            case "HK" -> "HKD";
            case "US" -> "USD";
            default -> "CNY";
        };
    }

    /** 展示时区：按交易所市场时区（CN→Asia/Shanghai、HK→Asia/Hong_Kong、US→America/New_York）。 */
    public String timeZoneOf(String market) {
        return switch (market) {
            case "HK" -> "Asia/Hong_Kong";
            case "US" -> "America/New_York";
            default -> "Asia/Shanghai";
        };
    }

    /** 日历查询用市场代码：CN 归一到 CN_A；HK/US 原样。 */
    public String marketCodeOf(String market) {
        return switch (market) {
            case "HK" -> "HK";
            case "US" -> "US";
            default -> WorkbenchConstants.MARKET_CN_A;
        };
    }
}
