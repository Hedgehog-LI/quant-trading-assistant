package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 内容哈希流式行（manifest 有序 symbol|date|row_hash）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfManifestRowHashDO {
    private String canonicalSymbol;
    private LocalDate tradeDate;
    private String rowHash;
}
