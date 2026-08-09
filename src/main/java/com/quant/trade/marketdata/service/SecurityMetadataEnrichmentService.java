package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.exception.SecurityDirectoryNotFoundException;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichReason;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichRequest;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichResult;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichFields;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import com.quant.trade.marketdata.vo.SecurityMetadataEnrichVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 证券元数据按需补全编排服务（D3-03）。
 * <p>
 * 流程：规范化 canonical symbol（非法→INVALID_CANONICAL_SYMBOL 400）→
 * 本地目录存在性校验（缺失→{@link SecurityDirectoryNotFoundException} 404 STOCK_NOT_FOUND）→
 * 调用 enricher（disabled 抛 BUSINESS_RULE_VIOLATION 400；外部失败透传具体码 400）→
 * 可选持久化。
 * <p>
 * 事务边界：本方法<b>不使用</b> {@code @Transactional}。LongPort Static Info 网络调用
 * 必须在数据库事务外执行；只有最终的单条条件 UPDATE 保持原子性（单语句天然原子）。
 * 因此这里没有 Spring self-invocation 事务代理问题，也不需要 TransactionTemplate。
 * <p>
 * 持久化语义（persist=true）由 Mapper 的<b>原子条件更新</b>保证（见
 * {@code StockBasicMapper.updateEmptyMetadataByCanonicalSymbol} 与 XML）：
 * <ul>
 *   <li>只在数据库当前字段仍为 null/空字符串/纯空白时写入；本地已有非空字段无论
 *       LongPort 返回值是否不同都保留本地值（数据库层保证，不依赖调用前读取结果）。</li>
 *   <li>不修改 {@code source_updated_at} / {@code data_source} / {@code source_hash}，
 *       不污染证券目录新鲜度。</li>
 *   <li>必须检查受影响行数；为 0 时重新查询：行不存在→STOCK_NOT_FOUND 404，
 *       行存在但无可补字段→persisted=false + reason=NO_CHANGE。</li>
 * </ul>
 * provider 抛出的 {@link BusinessException} 一律透传，不包装；响应绝不包含凭据/token。
 */
@Service
@RequiredArgsConstructor
public class SecurityMetadataEnrichmentService {

    private final SecurityMetadataEnricher enricher;
    private final StockBasicMapper stockBasicMapper;

    /**
     * 按需补全单符号元数据。
     *
     * @param rawCanonicalSymbol 原始 canonical symbol（将被规范化）
     * @param persist            true 时由数据库层原子条件更新 stock_basic 空字段
     * @return 补全结果 VO（顶层 lotSize）
     */
    public SecurityMetadataEnrichVO enrich(String rawCanonicalSymbol, boolean persist) {
        final String canonicalSymbol;
        try {
            canonicalSymbol = CanonicalSymbolUtils.normalize(rawCanonicalSymbol);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL,
                    sanitize(exception.getMessage()));
        }

        // 本地目录存在性校验（缺失 → 404 STOCK_NOT_FOUND，避免对不存在证券发外部请求）。
        // 仅作前置校验；持久化决定完全交给数据库层原子条件更新，不用本次读取的旧字段值。
        StockBasicDO existing = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
        if (existing == null) {
            throw new SecurityDirectoryNotFoundException(canonicalSymbol);
        }

        // 调用 enricher（disabled 抛 BUSINESS_RULE_VIOLATION；外部失败/身份不一致透传具体码）。
        // 该外部网络调用位于任何数据库事务之外。
        EnrichResult result = enricher.enrich(new EnrichRequest(canonicalSymbol, persist));

        if (!result.enriched() || result.fields() == null) {
            // provider 无数据：200、enriched=false、persisted=false、reason=PROVIDER_NOT_FOUND，不落库。
            return toVO(result, false, EnrichReason.PROVIDER_NOT_FOUND);
        }
        if (!persist) {
            // 只查询展示：返回 fields + lotSize，不写库。
            return toVO(result, false, EnrichReason.OK);
        }

        EnrichReason reason = persistIfNeeded(canonicalSymbol, result);
        return toVO(result, EnrichReason.OK == reason, reason);
    }

    /**
     * 原子条件持久化：仅在数据库当前字段仍为空时写入。
     *
     * @return 实际写入返回 OK；无可补字段或行未修改返回 NO_CHANGE
     */
    private EnrichReason persistIfNeeded(String canonicalSymbol, EnrichResult result) {
        EnrichFields fields = result.fields();
        // 防御性规范化：null/空字符串/纯空白统一视为 null，禁止写入数据库。
        String nameCn = normalizeBlank(fields.nameCn());
        String nameHk = normalizeBlank(fields.nameHk());
        String nameEn = normalizeBlank(fields.nameEn());
        String exchange = normalizeBlank(fields.exchange());
        String currency = normalizeBlank(fields.currency());

        // provider 返回的值全部为 null/空白：无可补字段。
        if (nameCn == null && nameHk == null && nameEn == null
                && exchange == null && currency == null) {
            return EnrichReason.NO_CHANGE;
        }

        // 原子条件更新（单条 UPDATE，天然原子；不修改 source_updated_at/data_source/source_hash）。
        int affected = stockBasicMapper.updateEmptyMetadataByCanonicalSymbol(
                canonicalSymbol, nameCn, nameHk, nameEn, exchange, currency);
        if (affected > 0) {
            return EnrichReason.OK;
        }

        // 0 行受影响：行可能被并发删除，或全部目标字段已被本地/并发目录同步写为非空。
        StockBasicDO current = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
        if (current == null) {
            throw new SecurityDirectoryNotFoundException(canonicalSymbol);
        }
        return EnrichReason.NO_CHANGE;
    }

    /** 字符串规范化：trim；null/空字符串/纯空白统一视为 null。 */
    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SecurityMetadataEnrichVO toVO(EnrichResult result, boolean persisted, EnrichReason reason) {
        SecurityMetadataEnrichVO.EnrichFields fields = result.fields() == null
                ? new SecurityMetadataEnrichVO.EnrichFields(null, null, null, null, null)
                : new SecurityMetadataEnrichVO.EnrichFields(
                        result.fields().nameCn(), result.fields().nameHk(), result.fields().nameEn(),
                        result.fields().exchange(), result.fields().currency());
        return new SecurityMetadataEnrichVO(
                result.canonicalSymbol(), result.enriched(), result.providerCode(),
                fields, result.lotSize(), persisted, reason.name());
    }

    private String sanitize(String message) {
        String safe = message == null ? "证券代码格式不合法" : message;
        safe = safe.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }
}
