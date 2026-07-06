package com.quant.trade.marketdata.vo;

import java.util.List;

/** CSV 导入结果。 */
public record DailyBarImportResultVO(
    int inserted,
    int updated,
    int skipped,
    int failed,
    List<RowError> errors
) {
    public record RowError(int row, String message) {}
}
