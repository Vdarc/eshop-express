package com.traslation.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 商品列表的纯内存逻辑：图片分组、字段补齐、语言归一。
 *
 * <p>抽出来的目的有两个： 1. 让「一次查询覆盖整页图片」这件事可以被单测锁住（防 N+1 回归）； 2. 让 JDBC 调用只负责取数，不掺杂映射与兜底分支。
 */
final class ProductQuerySupport {

    /** 支持的目标语言；不在此集合内一律回落 zh，与 Flask 侧一致。 */
    static final Set<String> LANGS =
            Set.of("zh", "en", "ar", "fr", "de", "ja", "ko", "ru", "th", "vi", "ms", "id", "tl");

    private ProductQuerySupport() {}

    /** 语言参数归一：非法或缺失时回落 zh。 */
    static String normalizeLang(String language) {
        return language != null && LANGS.contains(language) ? language : "zh";
    }

    /**
     * 把一条 SQL 取回的图片行按 product_id 分组，保持 SQL 的 sort,id 顺序。
     *
     * @param imageRows 每行至少包含 product_id 与 image_url
     */
    static Map<Long, List<String>> groupImages(List<Map<String, Object>> imageRows) {
        Map<Long, List<String>> images = new HashMap<>();
        if (imageRows == null) return images;
        for (Map<String, Object> row : imageRows) {
            Object id = row.get("product_id");
            if (!(id instanceof Number number)) continue;
            images.computeIfAbsent(number.longValue(), k -> new ArrayList<>())
                    .add(Objects.toString(row.get("image_url"), ""));
        }
        return images;
    }

    /**
     * 把图片与派生字段写回商品行。
     *
     * <p>{@code image_url} 取首图，没有图片时为 null；{@code foreign_title/foreign_desc} 用于兼容前端旧字段名，保持与 Flask
     * 响应一致。
     */
    static void attachImages(
            List<Map<String, Object>> rows, Map<Long, List<String>> imagesByProduct) {
        if (rows == null) return;
        Map<Long, List<String>> images = imagesByProduct == null ? Map.of() : imagesByProduct;
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            List<String> urls =
                    id instanceof Number number
                            ? images.getOrDefault(number.longValue(), List.of())
                            : List.of();
            row.put("image_urls", urls);
            row.put("image_url", urls.isEmpty() ? null : urls.get(0));
            row.put("foreign_title", row.get("title"));
            row.put("foreign_desc", row.get("description"));
        }
    }

    /** 分页总页数：无数据时返回 0，避免前端出现 1 页空列表。 */
    static int totalPages(int total, int size) {
        return total <= 0 || size <= 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    /** 目标语言探测顺序（与 Flask 的 language_priority 一致，不含 zh）。 */
    private static final List<String> TARGET_LANG_PRIORITY =
            List.of("en", "ar", "fr", "de", "ja", "ko", "ru", "th", "vi", "ms", "id", "tl");

    /** 对外返回的 13 个语种，顺序与 Flask 一致。 */
    private static final List<String> ALL_LANGS =
            List.of("zh", "en", "ar", "fr", "de", "ja", "ko", "ru", "th", "vi", "ms", "id", "tl");

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null || b.isBlank() ? null : b;
    }

    /**
     * 组装单个商品的对外结构。
     *
     * <p>字段集合与取值规则严格对齐 Flask 的 {@code _format_product}： 13 语种标题/描述、目标语言探测、无图占位、按国家换算的价格。
     *
     * @param row 商品行（`select p.*` 的结果，列名即字段名）
     * @param imageUrls 已按 sort 排序的图片地址
     * @param publishTargetLang 发布记录里的首个目标语言，可为 null
     * @param country 用户国家，用于价格换算，可为 null
     */
    static Map<String, Object> formatProduct(
            Map<String, Object> row,
            List<String> imageUrls,
            String publishTargetLang,
            String country,
            int placeholderSize,
            boolean placeholderWhenEmpty) {
        String esin = str(row.get("esin"));

        List<String> urls = imageUrls == null || imageUrls.isEmpty() ? List.of() : imageUrls;
        if (urls.isEmpty() && placeholderWhenEmpty) {
            // Flask 用 hash(esin) 生成占位图，Python 的 hash 每次进程启动都不同，
            // 这里用稳定哈希代替，保证同一商品每次得到同一张图。
            int seed = Math.abs(esin == null ? 0 : esin.hashCode()) % 1000;
            urls =
                    List.of(
                            "https://picsum.photos/seed/"
                                    + seed
                                    + "/"
                                    + placeholderSize
                                    + "/"
                                    + placeholderSize);
        }

        String target =
                publishTargetLang != null && !publishTargetLang.isBlank()
                        ? publishTargetLang
                        : "en";
        String potential = str(row.get(target + "_title"));
        if (potential == null || potential.isBlank() || potential.equals(esin)) {
            for (String lang : TARGET_LANG_PRIORITY) {
                String candidate = str(row.get(lang + "_title"));
                if (candidate != null && !candidate.isBlank() && !candidate.equals(esin)) {
                    target = lang;
                    break;
                }
            }
        }

        String foreignTitle = str(row.get(target + "_title"));
        if (foreignTitle == null || foreignTitle.isBlank() || foreignTitle.equals(esin))
            foreignTitle = firstNonBlank(str(row.get("en_title")), str(row.get("zh_title")));

        String foreignDesc = str(row.get(target + "_desc"));
        if (foreignDesc == null || foreignDesc.isBlank() || foreignDesc.equals(esin))
            foreignDesc = firstNonBlank(str(row.get("en_desc")), str(row.get("zh_desc")));

        double priceCny = 0.0;
        Object price = row.get("price");
        if (price instanceof Number number) priceCny = number.doubleValue();
        else if (price != null) {
            try {
                priceCny = Double.parseDouble(price.toString());
            } catch (NumberFormatException ignored) {
                priceCny = 0.0;
            }
        }

        Map<String, Object> product = new java.util.LinkedHashMap<>();
        product.put("esin", esin);
        product.put("category", row.get("category"));
        for (String lang : ALL_LANGS) {
            product.put(lang + "_title", row.get(lang + "_title"));
            product.put(lang + "_desc", row.get(lang + "_desc"));
        }
        product.put("foreign_title", foreignTitle);
        product.put("foreign_desc", foreignDesc);
        product.put("target_language", target);
        product.put("country", row.get("country"));
        product.put("price", priceCny);
        product.put("display_price", CurrencyRates.convert(priceCny, country));
        product.put("image_url", urls.isEmpty() ? null : urls.get(0));
        product.put("image_urls", urls);
        return product;
    }
}
