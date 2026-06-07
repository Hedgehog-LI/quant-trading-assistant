package com.quant.trade.common.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 标签工具类。
 * <p>
 * 处理 tag list 与数据库逗号分隔字符串之间的转换，
 * 以及标签的清洗和校验。
 */
public final class TagUtils {

    /** 标签分隔符 */
    private static final String TAG_SEPARATOR = ",";

    private TagUtils() {
    }

    /**
     * 将逗号分隔的字符串转换为标签列表。
     * <p>
     * 自动去除首尾空白，过滤空值。
     *
     * @param tagStr 逗号分隔的标签字符串，例如 "FOMO,FEAR,CALM"
     * @return 标签列表，例如 ["FOMO", "FEAR", "CALM"]；如果输入为空则返回空列表
     */
    public static List<String> fromString(String tagStr) {
        if (StringUtils.isBlank(tagStr)) {
            return Collections.emptyList();
        }
        return Stream.of(tagStr.split(TAG_SEPARATOR))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());
    }

    /**
     * 将标签列表转换为逗号分隔的字符串。
     *
     * @param tags 标签列表
     * @return 逗号分隔的字符串，例如 "FOMO,FEAR,CALM"；如果列表为空则返回 null
     */
    public static String toString(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(TAG_SEPARATOR));
    }
}
