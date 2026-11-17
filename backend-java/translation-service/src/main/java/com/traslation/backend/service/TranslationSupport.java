package com.traslation.backend.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 翻译模块的字符串/数值辅助逻辑。
 *
 * <p>翻译历史用「分号 + 空格」把数组存成单列字符串（分隔符为分号加空格）， 写入格式固定，读取时额外兼容只有分号没有空格的历史数据。
 */
final class TranslationSupport {

    /** 存储数组时使用的分隔符。 */
    static final String SEPARATOR = "; ";

    /** 读取时兼容 "; " 与 ";" 两种写法。 */
    private static final String SPLIT_REGEX = ";\\s*";

    private TranslationSupport() {}

    /** 列表 → 存储字符串；非集合类型返回空串。 */
    static String joinValues(Object value) {
        if (value instanceof Collection<?> collection)
            return String.join(
                    SEPARATOR,
                    collection.stream().filter(Objects::nonNull).map(Objects::toString).toList());
        return "";
    }

    /** 存储字符串 → 列表；空白返回空列表，避免产生 [""] 这种脏元素。 */
    static List<String> splitValues(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(SPLIT_REGEX)).filter(s -> !s.isBlank()).toList();
    }

    /** 金额保留两位小数（成本统计用）。 */
    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
