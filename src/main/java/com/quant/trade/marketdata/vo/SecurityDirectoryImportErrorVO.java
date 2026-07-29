package com.quant.trade.marketdata.vo;

/** 有界且脱敏的目录导入错误。 */
public record SecurityDirectoryImportErrorVO(
        long line,
        String field,
        String reasonCode,
        String message
) {
}
