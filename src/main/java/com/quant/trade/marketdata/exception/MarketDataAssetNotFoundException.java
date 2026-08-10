package com.quant.trade.marketdata.exception;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;

/**
 * 行情资产查询目标证券不存在的 404 业务异常。
 * <p>
 * 复用 {@code STOCK_NOT_FOUND}，由 {@code MarketDataAssetController} 的
 * controller 级 handler 返回 HTTP 404（不能被全局 handler 固定映射成 400）。
 */
public class MarketDataAssetNotFoundException extends BusinessException {
    public MarketDataAssetNotFoundException(String canonicalSymbol) {
        super(ErrorCodeEnum.STOCK_NOT_FOUND, "证券不存在: " + canonicalSymbol);
    }
}
