package com.quant.trade.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 所有业务逻辑中需要中断并返回错误信息的场景，应抛出此异常，
 * 由 {@link GlobalExceptionHandler} 统一捕获并转换为标准 API 响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCodeEnum errorCode;

    public BusinessException(ErrorCodeEnum errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }
}
