package com.traslation.backend.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecommendationProfileService {
    private static final Map<String, String> LANGUAGES =
            Map.ofEntries(
                    Map.entry("美国", "en"),
                    Map.entry("英国", "en"),
                    Map.entry("法国", "fr"),
                    Map.entry("德国", "de"),
                    Map.entry("日本", "ja"),
                    Map.entry("韩国", "ko"),
                    Map.entry("中国", "zh"),
                    Map.entry("俄罗斯", "ru"),
                    Map.entry("沙特", "ar"),
                    Map.entry("阿联酋", "ar"),
                    Map.entry("泰国", "th"),
                    Map.entry("越南", "vi"),
                    Map.entry("马来西亚", "ms"),
                    Map.entry("印尼", "id"),
                    Map.entry("菲律宾", "tl"),
                    Map.entry("新加坡", "en"));
    private static final Map<String, Map<String, List<String>>> CULTURE_TAGS =
            Map.ofEntries(
                    Map.entry(
                            "沙特",
                            categories(
                                    "保守|长款|长袍|纯色|防晒|穆斯林|白色|金色|透气|遮肤",
                                    "轻奢|大屏|续航强|耐热|防尘|高端商务|伊斯兰审美")),
                    Map.entry(
                            "阿联酋",
                            categories("奢华|高端|修身简约|金饰搭配|仪式感|轻薄防晒", "旗舰机型|快充|轻奢外观|车载生态|影音顶配")),
                    Map.entry("美国", categories("休闲|街头|运动|百搭|简约|个性|大码|户外", "性能强悍|极客风|电竞|便携|黑科技|耐用")),
                    Map.entry("英国", categories("英伦复古|绅士风|简约通勤|格纹|质感正装", "商务简约|复古数码|安全合规|轻奢办公")),
                    Map.entry("法国", categories("法式优雅|轻奢显瘦|小众设计|浪漫纯色|精致剪裁", "美学外观|轻薄便携|影音艺术|高颜值数码")),
                    Map.entry(
                            "德国",
                            categories("严谨简约|质感耐用|商务通勤|纯色百搭|舒适实用", "精工品质|稳定耐用|商务高效|环保节能|实用功能")),
                    Map.entry(
                            "日本", categories("极简|通勤百搭|轻薄|素雅|合身|日常简约", "小巧精致|低噪节能|耐用稳定|精密做工|便携迷你")),
                    Map.entry("韩国", categories("韩系修身|潮流ins|小众百搭|显瘦网红|少女风", "高颜值超薄|快充闪充|影音娱乐|潮流数码")),
                    Map.entry("俄罗斯", categories("加厚保暖|宽松抗寒|耐磨户外|深色系|防风", "低温续航|坚固防摔|大功率|重工业风")),
                    Map.entry("泰国", categories("轻薄透气|热带速干|亮色休闲|防汗防晒", "平价耐用|散热好|大屏追剧|防水防汗")),
                    Map.entry("越南", categories("平价基础款|轻薄通勤|纯色简约|日常耐穿", "高性价比|入门实用|超长待机")),
                    Map.entry("马来西亚", categories("穆斯林休闲|透气遮肤|热带轻薄|素雅保守", "耐热防尘|家用刚需|平价旗舰")),
                    Map.entry("印尼", categories("宽松穆斯林服饰|防紫外线|朴素百搭", "大容量电池|耐热防潮|全民通用款")),
                    Map.entry("菲律宾", categories("热带休闲|透气轻薄|亮色日常|平价百搭", "高性价比|大屏娱乐|续航持久|实用入门")),
                    Map.entry(
                            "新加坡",
                            categories("简约通勤|透气轻薄|职场休闲|干净利落|热带舒适", "便携轻薄|高效商务|稳定耐用|都市轻奢|散热优秀")),
                    Map.entry("中国", categories("国潮|喜庆|性价比|通勤百搭|保暖舒适", "快充黑科技|高性价比|国货旗舰|智能生态")));

    public Map<String, Object> build(String country, String category, String keyword) {
        String normalizedCategory = normalizeCategory(category);
        // Map.ofEntries 对 null key 会抛 NPE，这里显式兜底，避免缺省参数打崩接口
        String normalizedCountry =
                country == null || !LANGUAGES.containsKey(country) ? "沙特" : country;
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("country", normalizedCountry);
        profile.put("language", LANGUAGES.get(normalizedCountry));
        profile.put("category", normalizedCategory);
        profile.put("keyword", keyword);
        profile.put("culture_tags", tags(normalizedCountry, normalizedCategory));
        return profile;
    }

    public String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "clothes";
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
                // 兼容前端下拉值「服装」、发布页「3C电子」以及内部键 clothes/3c
            case "服装", "服饰", "clothes", "clothing" -> "clothes";
            case "电子产品", "数码", "3c", "3c电子", "3c电子产品" -> "3c";
            default -> "clothes";
        };
    }

    public List<String> tags(String country, String category) {
        // 不可变 Map 对 null key 会抛 NPE，先做显式判空
        if (country == null || category == null) return List.of();
        Map<String, List<String>> values = CULTURE_TAGS.getOrDefault(country, Map.of());
        return values.getOrDefault(category, List.of());
    }

    private static Map<String, List<String>> categories(String clothes, String electronics) {
        return Map.of(
                "clothes", List.of(clothes.split("\\|")), "3c", List.of(electronics.split("\\|")));
    }
}
