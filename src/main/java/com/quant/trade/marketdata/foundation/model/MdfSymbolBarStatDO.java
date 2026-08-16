package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 质量检查用的日 K 聚合行（GROUP BY canonical_symbol）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfSymbolBarStatDO {
    private String canonicalSymbol;
    private Long rowCount;
    private LocalDate firstDate;
    private LocalDate lastDate;
}
