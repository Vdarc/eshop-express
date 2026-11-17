package com.traslation.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 商品发布的纯逻辑：品类映射与平台合规校验。
 *
 * <p>抽成静态工具便于单测——这两处都属于「错了不报错、只是结果不对」的静默缺陷： 品类映射错会让术语匹配与翻译上下文失效，合规规则错会放过违规文案。
 */
public final class PublishSupport {

    /** 前端英文品类 key → 中文品类名（翻译与术语匹配使用中文）。 */
    public static final Map<String, String> CATEGORY_CN =
            Map.of(
                    "clothes", "服装",
                    "3c", "3C电子",
                    "home", "家居用品",
                    "beauty", "美妆护肤",
                    "baby", "母婴用品",
                    "sports", "运动户外",
                    "car", "汽车用品");

    /** 各平台禁用词，与 Flask 的 PLATFORM_RULES 保持一致。 */
    private static final Map<String, List<String>> BANNED =
            Map.of(
                    "amazon", List.of("free", "guarantee", "best", "amazing"),
                    "ebay", List.of("free shipping", "click here"),
                    "aliexpress", List.of("wholesale", "dropshipping"));

    /** 已知平台的字符上限。 */
    private static final int MAX_LENGTH = 512;

    private PublishSupport() {}

    /** 英文品类 key 转中文；未知品类回退「服装」（与 Flask 默认一致）。 */
    public static String categoryCn(String category) {
        if (category == null || category.isBlank()) return "服装";
        return CATEGORY_CN.getOrDefault(category.toLowerCase(Locale.ROOT), "服装");
    }

    /**
     * 单平台合规校验。
     *
     * <p>未知平台返回通过且 {@code limit = Integer.MAX_VALUE}——对应 Flask 的 {@code
     * float('inf')}，语义是「该平台无规则，不做长度限制」。
     */
    public static Map<String, Object> checkCompliance(String text, String platform) {
        String content = text == null ? "" : text;
        List<String> words = BANNED.get(platform == null ? "" : platform.toLowerCase(Locale.ROOT));
        if (words == null)
            return Map.of(
                    "passed",
                    true,
                    "current",
                    content.length(),
                    "limit",
                    Integer.MAX_VALUE,
                    "messages",
                    List.of());
        List<String> found =
                words.stream()
                        .filter(word -> content.toLowerCase(Locale.ROOT).contains(word))
                        .toList();
        List<String> messages = new ArrayList<>();
        if (content.length() > MAX_LENGTH)
            messages.add("超出字符限制 (" + content.length() + "/" + MAX_LENGTH + ")");
        if (!found.isEmpty()) messages.add("包含禁用词: " + String.join(", ", found));
        return Map.of(
                "passed",
                content.length() <= MAX_LENGTH && found.isEmpty(),
                "current",
                content.length(),
                "limit",
                MAX_LENGTH,
                "messages",
                messages);
    }
}
