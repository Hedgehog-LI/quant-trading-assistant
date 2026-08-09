package com.quant.trade.marketdata.provider;

/**
 * 证券元数据按需补全 Provider 抽象（D3-03）。
 * <p>
 * 职责：对本地目录中已存在的精确 canonical symbol，调用外部 provider 拉取静态信息
 * （多语言名称、交易所、币种、每手股数等）并以 {@link EnrichResult} 形式返回。
 * 只读外部接口，不接报价/K 线/交易/账户/订单；持久化由
 * {@link com.quant.trade.marketdata.service.SecurityMetadataEnrichmentService} 决定。
 * <p>
 * 实现必须只读、可审计、不打印或返回任何凭据/密钥/完整 token。
 */
public interface SecurityMetadataEnricher {

    /** Provider 是否已启用（配置 enabled 且凭据/SDK 就绪）。disabled 时由兜底实现呈现。 */
    boolean isEnabled();

    /** Provider 唯一标识，如 LONGPORT；disabled 兜底返回固定 disabled code。 */
    String getProviderCode();

    /**
     * 按精确 canonical symbol 拉取元数据。
     *
     * @param request 补全请求（canonical symbol + persist 提示，由实现据 provider 能力解读）
     * @return 补全结果（provider 无数据时返回 {@code enriched=false} + reason=PROVIDER_NOT_FOUND，不抛异常）
     * @throws com.quant.trade.common.exception.BusinessException provider 调用失败或证券身份不一致时透传
     *                                                            具体错误码；disabled 实现直接抛
     *                                                            BUSINESS_RULE_VIOLATION
     */
    EnrichResult enrich(EnrichRequest request);

    /** 补全请求。persist 仅作为提示，是否真正落库由 service 决定（provider 不写库）。 */
    record EnrichRequest(String canonicalSymbol, boolean persist) {
    }

    /**
     * 补全结果。形状与响应 VO {@link com.quant.trade.marketdata.vo.SecurityMetadataEnrichVO} 端到端一致。
     * <p>
     * {@code lotSize} 是顶层字段（不在 {@link EnrichFields} 内）——这是已决定的设计妥协：
     * stock_basic 无 lot_size 列，捕获后只在响应中返回，不持久化、不扩表。
     *
     * @param canonicalSymbol 本系统统一代码（provider 验证一致后回填）
     * @param enriched        是否成功拿到 provider 静态信息（false=provider 未找到数据，非异常）
     * @param providerCode    provider 标识；disabled/失败前置时可空
     * @param fields          可补全的字段集合（均可空）
     * @param lotSize         每手股数（顶层 Integer，可空；不持久化）
     * @param reason          结果原因枚举
     */
    record EnrichResult(String canonicalSymbol, boolean enriched, String providerCode,
                        EnrichFields fields, Integer lotSize, EnrichReason reason) {
    }

    /** 可补全的元数据字段集合，均可能为 null（provider 未返回或与本地一致）。 */
    record EnrichFields(String nameCn, String nameHk, String nameEn, String exchange, String currency) {
    }

    /** 补全结果原因枚举（与响应 reason 字符串一一对应）。 */
    enum EnrichReason {
        /** provider 返回数据；persist=false 展示，或 persist=true 实际写入。 */
        OK,
        /** persist=true 但没有任何字段被写入（无可补字段或行未被修改）。 */
        NO_CHANGE,
        /** provider 返回 null，未找到该证券的静态信息；不落库，reason 不允许用 OK 掩盖。 */
        PROVIDER_NOT_FOUND
    }
}
