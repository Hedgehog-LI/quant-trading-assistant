package com.quant.trade.marketdata.dto;

import java.time.LocalDate;

/** 编辑证券主数据请求（JSON Body）。 */
public record UpdateStockBasicDTO(
    String name,
    LocalDate listDate,
    Boolean delisted
) {}
