package com.quant.trade.marketdata.enums;

/** 可解释的证券搜索命中渠道，枚举顺序与同分优先级一致。 */
public enum SecurityMatchedByEnum {
    CANONICAL_SYMBOL_EXACT,
    RAW_SYMBOL_EXACT,
    FORMAL_NAME_EXACT,
    FORMAL_NAME_PREFIX,
    ALIAS_EXACT,
    ALIAS_PREFIX,
    PINYIN_FULL_PREFIX,
    PINYIN_ABBR_PREFIX,
    NAME_CONTAINS,
    ALIAS_CONTAINS
}
