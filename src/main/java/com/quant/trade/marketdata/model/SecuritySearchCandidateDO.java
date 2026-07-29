package com.quant.trade.marketdata.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 搜索候选及 SQL 已判定的别名命中标志。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecuritySearchCandidateDO extends StockBasicDO {
    private Boolean aliasExact;
    private Boolean aliasPrefix;
    private Boolean aliasContains;
}
