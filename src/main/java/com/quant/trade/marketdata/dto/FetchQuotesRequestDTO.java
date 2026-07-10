package com.quant.trade.marketdata.dto;

import java.util.List;

/** 获取最新行情请求 DTO。 */
public record FetchQuotesRequestDTO(
    List<String> canonicalSymbols,
    Boolean persist
) {}
