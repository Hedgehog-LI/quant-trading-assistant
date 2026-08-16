package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 覆盖期望输入：范围证券 + stock_basic 上市日（缺失显式假设为窗口起点，R1 §四.7）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfSymbolExpectationDO {
    private String canonicalSymbol;
    private LocalDate listDate;
}
