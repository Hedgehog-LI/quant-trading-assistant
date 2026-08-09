package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 证券元数据按需补全请求（D3-03）。
 * <p>
 * 仅接受精确 canonical symbol（经 {@code CanonicalSymbolUtils.normalize}），不做名称模糊搜索；
 * persist 默认 false（只校验/展示），true 时由 service 通过数据库层原子条件更新空字段。
 */
public record SecurityMetadataEnrichRequestDTO(
        @NotBlank(message = "canonicalSymbol 不能为空")
        @Size(max = 32, message = "canonicalSymbol 长度不能超过 32")
        String canonicalSymbol,
        Boolean persist) {

    /** persist 视为 false 的便捷访问器：null 或缺失时按 false 处理。 */
    public boolean persistOrDefault() {
        return Boolean.TRUE.equals(persist);
    }
}
