package com.quant.trade.marketdata.exception;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;

/** 新目录详情接口专用的 404 业务异常。 */
public class SecurityDirectoryNotFoundException extends BusinessException {
    public SecurityDirectoryNotFoundException(String canonicalSymbol) {
        super(ErrorCodeEnum.STOCK_NOT_FOUND, "证券目录不存在: " + canonicalSymbol);
    }
}
