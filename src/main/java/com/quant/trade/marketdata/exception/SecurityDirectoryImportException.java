package com.quant.trade.marketdata.exception;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportResultVO;
import lombok.Getter;

/** 携带结构化行错误的目录导入异常。 */
@Getter
public class SecurityDirectoryImportException extends BusinessException {
    private final SecurityDirectoryImportResultVO result;

    public SecurityDirectoryImportException(ErrorCodeEnum errorCode, String message,
                                            SecurityDirectoryImportResultVO result) {
        super(errorCode, message);
        this.result = result;
    }
}
