package com.quant.trade.marketdata.vo;

import java.util.List;

/** 本地证券目录 CSV 导入计数。 */
public record SecurityDirectoryImportResultVO(
        long totalRows,
        long inserted,
        long updated,
        long unchanged,
        long aliasesInserted,
        long aliasesUnchanged,
        long formerNamesAdded,
        long failed,
        List<SecurityDirectoryImportErrorVO> errors
) {
}
