package com.quant.trade.marketdata.util;

import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 目录搜索与别名共用的 Unicode 稳定规范化。 */
public final class SecurityTextNormalizer {

    private SecurityTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    /** Escapes a normalized value for a literal SQL LIKE match using {@code !} as the escape character. */
    public static String escapeLikeLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /** Binary identity for normalized text; UTF-8 preserves Unicode code-point distinctions across databases. */
    public static byte[] identityKey(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.getBytes(StandardCharsets.UTF_8);
    }
}
