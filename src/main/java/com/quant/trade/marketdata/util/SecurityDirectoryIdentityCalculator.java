package com.quant.trade.marketdata.util;

import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 证券目录快照内容身份计算（与 CSV/文件协议无关的纯工具）。
 * snapshotHash = 对规范化后的 stock 字段集合 + 排序后的 alias identity 集合的稳定 SHA-256。
 * 该类不解析 CSV、不读文件；仅消费已标准化的 DO 列表，保证 sync service 不被标记为 file-protocol。
 */
public final class SecurityDirectoryIdentityCalculator {

    private SecurityDirectoryIdentityCalculator() {}

    public static String computeSnapshotHash(String providerCode, String mode,
                                             List<StockBasicDO> stocks, List<StockAliasDO> aliases) {
        MessageDigest digest = newSha256();
        digest.update(utf8(providerCode));
        digest.update((byte) 0x1f);
        digest.update(utf8(mode));
        digest.update((byte) 0x1f);
        stocks.stream()
                .map(SecurityDirectoryIdentityCalculator::canonicalStockKey)
                .sorted()
                .forEachOrdered(key -> { digest.update(utf8(key)); digest.update((byte) 0x1e); });
        digest.update((byte) 0x1f);
        aliases.stream()
                .map(SecurityDirectoryIdentityCalculator::canonicalAliasKey)
                .sorted()
                .forEachOrdered(key -> { digest.update(utf8(key)); digest.update((byte) 0x1e); });
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String snapshotIdFromHash(String snapshotHash) {
        return snapshotHash == null ? null : snapshotHash.substring(0, Math.min(16, snapshotHash.length()));
    }

    /** D1 sameDirectoryData 等价语义（同步 staging/diff 与 provider 共享）。 */
    public static boolean sameDirectoryData(StockBasicDO left, StockBasicDO right) {
        java.util.Objects.requireNonNull(left);
        java.util.Objects.requireNonNull(right);
        return java.util.Objects.equals(left.getCanonicalSymbol(), right.getCanonicalSymbol())
                && java.util.Objects.equals(left.getSymbol(), right.getSymbol())
                && java.util.Objects.equals(left.getName(), right.getName())
                && java.util.Objects.equals(left.getMarket(), right.getMarket())
                && java.util.Objects.equals(left.getNameCn(), right.getNameCn())
                && java.util.Objects.equals(left.getNameHk(), right.getNameHk())
                && java.util.Objects.equals(left.getNameEn(), right.getNameEn())
                && java.util.Objects.equals(left.getShortName(), right.getShortName())
                && java.util.Objects.equals(left.getPinyinFull(), right.getPinyinFull())
                && java.util.Objects.equals(left.getPinyinAbbr(), right.getPinyinAbbr())
                && java.util.Objects.equals(left.getExchange(), right.getExchange())
                && java.util.Objects.equals(left.getCurrency(), right.getCurrency())
                && java.util.Objects.equals(left.getSecurityType(), right.getSecurityType())
                && java.util.Objects.equals(left.getListStatus(), right.getListStatus())
                && java.util.Objects.equals(left.getDataSource(), right.getDataSource())
                && java.util.Objects.equals(left.getSourceUpdatedAt(), right.getSourceUpdatedAt())
                && java.util.Objects.equals(left.getSourceHash(), right.getSourceHash())
                && java.util.Objects.equals(left.getListDate(), right.getListDate())
                && java.util.Objects.equals(left.getDelisted(), right.getDelisted());
    }

    private static String canonicalStockKey(StockBasicDO stock) {
        return SecurityTextNormalizer.normalize(stock.getCanonicalSymbol()) + "|"
                + SecurityTextNormalizer.normalize(stock.getName()) + "|"
                + stock.getMarket() + "|" + stock.getExchange() + "|" + stock.getCurrency() + "|"
                + stock.getSecurityType() + "|" + stock.getListStatus() + "|"
                + stock.getDataSource() + "|" + stock.getSourceUpdatedAt() + "|"
                + stock.getSourceHash() + "|" + stock.getListDate() + "|" + stock.getDelisted();
    }

    private static String canonicalAliasKey(StockAliasDO alias) {
        return alias.getAliasType() + "|" + alias.getNormalizedAlias() + "|"
                + alias.getLanguage() + "|" + alias.getDataSource();
    }

    private static byte[] utf8(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
