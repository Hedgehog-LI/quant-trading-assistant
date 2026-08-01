package com.quant.trade.marketdata.provider;

import com.quant.trade.common.exception.ErrorCodeEnum;

/**
 * 证券目录 Provider 拉取/标准化失败。携带稳定 reasonCode 与可解释消息，
 * 不包含原始文件路径或凭据。
 */
public class SecurityDirectoryProviderException extends RuntimeException {
    private final ErrorCodeEnum errorCode;
    private final String reasonCode;

    public SecurityDirectoryProviderException(ErrorCodeEnum errorCode, String reasonCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.reasonCode = reasonCode;
    }

    public ErrorCodeEnum getErrorCode() { return errorCode; }
    public String getReasonCode() { return reasonCode; }
}
