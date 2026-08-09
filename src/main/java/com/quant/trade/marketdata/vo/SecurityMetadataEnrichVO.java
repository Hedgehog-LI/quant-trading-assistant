package com.quant.trade.marketdata.vo;

/**
 * 证券元数据按需补全响应（D3-03）。
 * <p>
 * 注意：{@code lotSize} 是顶层字段（不在 {@link EnrichFields} 内）——这是已决定的设计妥协，
 * stock_basic 无 lot_size 列，捕获后只在响应中返回，不持久化、不扩表。
 *
 * @param canonicalSymbol 本系统统一代码
 * @param enriched        是否成功拿到 provider 静态信息
 * @param providerCode    provider 标识（可能为 null）
 * @param fields          可补全字段集合（均可空）
 * @param lotSize         每手股数（顶层 Integer，可空；不持久化）
 * @param persisted       是否实际写入 stock_basic 行
 * @param reason          结果原因：OK / NO_CHANGE / PROVIDER_NOT_FOUND
 */
public record SecurityMetadataEnrichVO(
        String canonicalSymbol,
        boolean enriched,
        String providerCode,
        EnrichFields fields,
        Integer lotSize,
        boolean persisted,
        String reason) {

    /** 可补全的元数据字段集合，均可能为 null。 */
    public record EnrichFields(String nameCn, String nameHk, String nameEn, String exchange, String currency) {
    }
}
