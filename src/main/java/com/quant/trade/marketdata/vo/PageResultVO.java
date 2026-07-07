package com.quant.trade.marketdata.vo;

import java.util.List;

/** 分页结果 VO。 */
public record PageResultVO<T>(
    List<T> items,
    long total,
    int page,
    int size
) {}
