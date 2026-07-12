package com.quant.trade.marketdata.dto;

import com.quant.trade.marketdata.constant.MarketDataConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 获取最新行情请求 DTO。 */
public record FetchQuotesRequestDTO(
    @NotEmpty(message = "canonicalSymbols 不能为空")
    @Size(max = MarketDataConstants.LONGPORT_MAX_QUOTE_SYMBOLS,
            message = "canonicalSymbols 单次最多支持 500 个标的")
    List<@NotBlank(message = "canonicalSymbol 不能为空") String> canonicalSymbols,
    Boolean persist
) {}
