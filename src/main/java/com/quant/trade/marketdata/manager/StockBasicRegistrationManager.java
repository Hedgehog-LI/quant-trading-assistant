package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 采集链路的最小证券身份登记器。
 * <p>
 * 采集计划只持有 canonical symbol，本组件负责在写入行情前幂等补齐
 * {@code stock_basic}。已有目录元数据不会被更新或覆盖。
 */
@Component
@RequiredArgsConstructor
public class StockBasicRegistrationManager {

    private static final String SECURITY_TYPE_STOCK = "STOCK";
    private static final String LIST_STATUS_UNKNOWN = "UNKNOWN";
    private static final String DATA_SOURCE_COLLECTION_PLAN = "COLLECTION_PLAN";

    private final StockBasicMapper stockBasicMapper;

    /** 幂等登记一组已通过计划校验的 canonical symbols。 */
    public void ensureRegistered(List<String> canonicalSymbols) {
        if (canonicalSymbols == null) {
            return;
        }
        canonicalSymbols.stream().map(CanonicalSymbolUtils::normalize).distinct()
                .map(this::minimalRecord)
                .forEach(stockBasicMapper::insertMinimalIfAbsent);
    }

    private StockBasicDO minimalRecord(String canonicalSymbol) {
        int separator = canonicalSymbol.indexOf('.');
        String market = canonicalSymbol.substring(0, separator);
        return StockBasicDO.builder()
                .canonicalSymbol(canonicalSymbol)
                .symbol(canonicalSymbol.substring(separator + 1))
                .market(market)
                .exchange(exchangeOf(market))
                .currency(currencyOf(market))
                .securityType(SECURITY_TYPE_STOCK)
                .listStatus(LIST_STATUS_UNKNOWN)
                .dataSource(DATA_SOURCE_COLLECTION_PLAN)
                .delisted(false)
                .build();
    }

    private String exchangeOf(String market) {
        return switch (market) {
            case "SH" -> "SSE";
            case "SZ" -> "SZSE";
            case "BJ" -> "BSE";
            case "HK" -> "HKEX";
            default -> null;
        };
    }

    private String currencyOf(String market) {
        return switch (market) {
            case "HK" -> "HKD";
            case "US" -> "USD";
            default -> "CNY";
        };
    }
}
