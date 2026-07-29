package com.quant.trade.marketdata.util;

import java.text.Normalizer;
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
}
