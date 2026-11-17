package com.traslation.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RecommendationService {
    private static final List<Map<String, Object>> DEFAULT_PRODUCTS =
            List.of(
                    mock(1, "B08XJ8S7GQ", "Smart Bracelet", "3c", "中国", "智能生态", "高性价比"),
                    mock(2, "B07K1R3Q2T", "Wireless Earphones", "3c", "中国", "黑科技", "高性价比"),
                    mock(3, "B09N2X4Y5Z", "Portable Charger", "3c", "中国", "高性价比", "便携"));
    private final JdbcTemplate db;
    private final EmbeddingService embeddings;
    private final RecommendationCacheService cache;
    private final RecommendationProfileService profiles;
    private final CollaborativeData collaborative;
    private final ObjectMapper json;
    private final RestClient llm;
    private final String llmModel;
    private final boolean llmConfigured;
    private final String imageBaseUrl;
    private volatile List<Map<String, Object>> products = List.of();
    private volatile List<List<Float>> productVectors = List.of();
    private volatile boolean productsLoaded;

    public RecommendationService(
            JdbcTemplate db,
            EmbeddingService embeddings,
            RecommendationCacheService cache,
            RecommendationProfileService profiles,
            CollaborativeData collaborative,
            ObjectMapper json,
            @Value("${app.llm.base-url:https://api.deepseek.com}") String llmBaseUrl,
            @Value("${app.llm.api-key:}") String llmApiKey,
            @Value("${app.llm.model:deepseek-chat}") String llmModel,
            @Value("${app.gateway.public-url:http://localhost:10682}") String imageBaseUrl) {
        this.db = db;
        this.embeddings = embeddings;
        this.cache = cache;
        this.profiles = profiles;
        this.collaborative = collaborative;
        this.json = json;
        this.llmModel = llmModel;
        this.llmConfigured = llmApiKey != null && !llmApiKey.isBlank();
        this.imageBaseUrl = imageBaseUrl.replaceAll("/$", "");
        this.llm =
                RestClient.builder()
                        .baseUrl(llmBaseUrl.replaceAll("/$", ""))
                        .defaultHeader("Authorization", "Bearer " + Objects.toString(llmApiKey, ""))
                        .build();
    }

    public Map<String, Object> recommend(String rawCountry, String rawCategory, String keyword) {
        Map<String, Object> cached = cache.get(rawCountry, rawCategory, keyword);
        if (cached != null) return cached;
        Map<String, Object> user = profiles.build(rawCountry, rawCategory, keyword);
        String country = Objects.toString(user.get("country"));
        String category = Objects.toString(user.get("category"));
        try {
            loadProductsWhenNeeded();
            int topN = ThreadLocalRandom.current().nextInt(3, 6);
            List<Map<String, Object>> recommendations = hybrid(user, topN);
            Map<String, Object> evaluation = evaluate(user, recommendations);
            recordExposure(recommendations);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("user_profile", user);
            result.put("recommendations", recommendations);
            result.put("evaluation", evaluation);
            cache.put(rawCountry, rawCategory, keyword, result);
            return result;
        } catch (Exception e) {
            List<Map<String, Object>> fallback = fallbackProducts(3);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(
                    "user_profile",
                    Map.of("country", country, "category", category, "tags", List.of()));
            result.put("recommendations", fallback);
            result.put("evaluation", Map.of("相关性分数", 0, "本地化分数", 0));
            result.put("degraded", true);
            return result;
        }
    }

    public long clearCache(String country, String category) {
        synchronized (this) {
            productsLoaded = false;
            products = List.of();
            productVectors = List.of();
        }
        return cache.evict(country, category);
    }

    public boolean redisAvailable() {
        return cache.available();
    }

    public Map<String, Object> redisState() {
        return cache.state();
    }

    private synchronized void loadProductsWhenNeeded() {
        if (productsLoaded && !products.isEmpty()) return;
        List<Map<String, Object>> loaded;
        try {
            List<Map<String, Object>> rows =
                    db.queryForList(
                            "select id,esin,en_title,zh_title,category,country from product order"
                                + " by id");
            Map<Integer, Map<String, Object>> imageByProduct = loadImages();
            LinkedHashMap<String, Map<String, Object>> unique = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                int id = ((Number) row.get("id")).intValue();
                String esin = Objects.toString(row.get("esin"), "");
                String category = normalizeProductCategory(row.get("category"));
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("idx", id);
                p.put("id", esin);
                p.put("name_en", Objects.toString(row.get("en_title"), ""));
                p.put("category", category);
                p.put("country", Objects.toString(row.get("country"), "美国"));
                p.put("tags", new ArrayList<String>());
                Map<String, Object> image = imageByProduct.get(id);
                p.put("image_url", image == null ? null : image.get("image_url"));
                unique.putIfAbsent(esin, p);
            }
            loaded = new ArrayList<>(unique.values());
        } catch (Exception e) {
            loaded = List.of();
        }
        if (loaded.isEmpty()) loaded = mockProducts();
        products = loaded;
        productsLoaded = true;
        productVectors = buildProductVectors(loaded);
    }

    private Map<Integer, Map<String, Object>> loadImages() {
        Map<Integer, Map<String, Object>> out = new HashMap<>();
        try {
            for (Map<String, Object> row :
                    db.queryForList(
                            "select product_id,image_url from product_image order by"
                                + " product_id,sort")) {
                int id = ((Number) row.get("product_id")).intValue();
                if (!out.containsKey(id)) {
                    Map<String, Object> image = new HashMap<>();
                    image.put(
                            "image_url",
                            formatImageUrl(Objects.toString(row.get("image_url"), "")));
                    out.put(id, image);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private List<List<Float>> buildProductVectors(List<Map<String, Object>> source) {
        List<String> names =
                source.stream().map(p -> Objects.toString(p.get("name_en"), "")).toList();
        List<List<Float>> vectors = new ArrayList<>();
        try {
            for (int offset = 0; offset < names.size(); offset += 256) {
                Map<?, ?> response =
                        embeddings.embed(
                                names.subList(offset, Math.min(names.size(), offset + 256)),
                                "minilm-l12");
                for (Object row : (List<?>) response.get("vectors"))
                    vectors.add(floatVector((List<?>) row));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return vectors;
    }

    private List<Map<String, Object>> hybrid(Map<String, Object> user, int topN) {
        List<Map<String, Object>> content = contentBased(user, 6);
        List<Map<String, Object>> collab = collaborative(user, 6);
        if (collab.isEmpty())
            return content.isEmpty()
                    ? defaultRecommendations(topN)
                    : new ArrayList<>(content.subList(0, Math.min(topN, content.size())));
        LinkedHashMap<String, Map<String, Object>> combined = new LinkedHashMap<>();
        for (Map<String, Object> p : content)
            combined.putIfAbsent(Objects.toString(p.get("id")), p);
        for (Map<String, Object> p : collab) combined.putIfAbsent(Objects.toString(p.get("id")), p);
        if (combined.isEmpty()) return defaultRecommendations(topN);
        return combined.values().stream().limit(topN).toList();
    }

    private List<Map<String, Object>> collaborative(Map<String, Object> user, int topN) {
        float[][] matrix = collaborative.ratings();
        List<CollaborativeData.SimulatedUser> users = collaborative.users();
        if (users.isEmpty() || matrix.length == 0) return List.of();
        String country = Objects.toString(user.get("country")),
                category = Objects.toString(user.get("category"));
        List<Integer> similar =
                users.stream()
                        .filter(
                                u ->
                                        country.equals(u.country())
                                                && u.preferCategories().contains(category))
                        .map(CollaborativeData.SimulatedUser::userId)
                        .filter(i -> i >= 0 && i < matrix.length)
                        .toList();
        if (similar.isEmpty()) return List.of();
        int columns = matrix[0].length;
        double[] scores = new double[columns];
        for (int index : similar)
            for (int col = 0; col < Math.min(columns, matrix[index].length); col++)
                scores[col] += matrix[index][col] / similar.size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < columns; i++) order.add(i);
        order.sort(
                Comparator.<Integer>comparingDouble(i -> scores[i])
                        .reversed()
                        .thenComparing(Comparator.reverseOrder()));
        Map<Integer, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> product : products)
            byId.put(((Number) product.get("idx")).intValue(), product);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index : order) {
            Map<String, Object> p = byId.get(index);
            if (p != null && category.equals(p.get("category")) && country.equals(p.get("country")))
                result.add(p);
            if (result.size() == topN) break;
        }
        return result;
    }

    private List<Map<String, Object>> contentBased(Map<String, Object> user, int topN) {
        String country = Objects.toString(user.get("country")),
                category = Objects.toString(user.get("category"));
        String keyword = Objects.toString(user.get("keyword"), "");
        List<Map<String, Object>> candidates =
                products.stream().filter(p -> category.equals(p.get("category"))).toList();
        if (candidates.isEmpty()) return List.of();
        List<String> keywords = translateKeywords(keyword);
        double[] similarity = semanticScores(keyword, candidates);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            Map<String, Object> product = candidates.get(candidateIndex);
            String name = Objects.toString(product.get("name_en"), "").toLowerCase(Locale.ROOT);
            long keywordScore = keywords.stream().filter(word -> name.contains(word)).count();
            double countryBonus = country.equals(product.get("country")) ? 1.2 : 0.7;
            double finalScore =
                    (keywordScore
                                    + (candidateIndex < similarity.length
                                            ? similarity[candidateIndex] * 3
                                            : 0))
                            * countryBonus;
            product.put("_recommend_score", finalScore);
            scored.add(product);
        }
        scored.sort(
                Comparator.comparingDouble(
                        p -> -((Number) p.get("_recommend_score")).doubleValue()));
        List<Map<String, Object>> tops =
                new ArrayList<>(scored.subList(0, Math.min(10, scored.size())));
        List<Map<String, Object>> filtered =
                keyword.isBlank() ? tops : llmFilterMainProducts(keyword, tops);
        if (filtered == null) filtered = tops;
        if (filtered.isEmpty()) filtered = tops;
        List<String> cultureTags = stringList(user.get("culture_tags"));
        for (Map<String, Object> product : filtered) {
            String lowerName =
                    Objects.toString(product.get("name_en"), "").toLowerCase(Locale.ROOT);
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            for (String tag : cultureTags)
                if (lowerName.contains(tag.toLowerCase(Locale.ROOT))) tags.add(tag);
            tags.addAll(
                    generateCultureTags(
                            Objects.toString(product.get("name_en"), ""), country, category));
            product.put("tags", new ArrayList<>(tags));
            product.remove("_recommend_score");
        }
        filtered.sort(Comparator.comparingDouble(p -> -tagScore(p, country, cultureTags)));
        return new ArrayList<>(filtered.subList(0, Math.min(topN, filtered.size())));
    }

    private List<String> translateKeywords(String keyword) {
        if (keyword.isBlank()) return List.of();
        if (!llmConfigured)
            return Arrays.stream(keyword.toLowerCase(Locale.ROOT).split("\\s+"))
                    .filter(s -> !s.isBlank())
                    .toList();
        String prompt =
                "请将用户输入的商品描述翻译成英文，并拆分成简洁、标准、可用于搜索匹配的英文关键词列表。\n用户描述："
                        + keyword
                        + "\n"
                        + "请输出JSON数组格式，不要其他内容。\n"
                        + "例如：输入\"亮色女式中码卫衣\" → [\"bright\",\"women\",\"medium\",\"hoodie\"]";
        try {
            Object parsed = json.readValue(callLlm(prompt, 0.1, true), Object.class);
            Collection<?> values =
                    parsed instanceof Collection<?> c
                            ? c
                            : parsed instanceof Map<?, ?> m ? m.keySet() : List.of();
            return values.stream()
                    .map(Objects::toString)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isBlank())
                    .toList();
        } catch (Exception e) {
            return Arrays.stream(keyword.toLowerCase(Locale.ROOT).split("\\s+"))
                    .filter(s -> !s.isBlank())
                    .toList();
        }
    }

    private double[] semanticScores(String keyword, List<Map<String, Object>> candidates) {
        double[] scores = new double[candidates.size()];
        if (keyword.isBlank() || productVectors.size() != products.size()) return scores;
        try {
            Map<?, ?> response = embeddings.embed(List.of(keyword), "minilm-l12");
            List<Float> query = floatVector((List<?>) ((List<?>) response.get("vectors")).get(0));
            for (int i = 0; i < candidates.size(); i++) {
                int productIndex = products.indexOf(candidates.get(i));
                List<Float> vector = productVectors.get(productIndex);
                double dot = 0;
                for (int j = 0; j < Math.min(query.size(), vector.size()); j++)
                    dot += query.get(j) * vector.get(j);
                scores[i] = dot;
            }
        } catch (Exception ignored) {
        }
        return scores;
    }

    private List<Map<String, Object>> llmFilterMainProducts(
            String keyword, List<Map<String, Object>> tops) {
        if (!llmConfigured) return tops;
        try {
            StringBuilder prompt =
                    new StringBuilder("你是电商商品筛选专家，用户需要找：【")
                            .append(keyword)
                            .append(
                                    "】\n"
                                            + "请判断每个商品是否为【主体商品】，不是配件/周边。\n"
                                            + "规则：\n"
                                            + "- 是主体 → \"main\"\n"
                                            + "- 配件 → \"accessory\"\n"
                                            + "- 不相关 → \"irrelevant\"\n"
                                            + "输出JSON：{\"1\":\"main\",\"2\":\"accessory\"}\n\n"
                                            + "商品列表：\n");
            for (int i = 0; i < tops.size(); i++)
                prompt.append(i + 1).append(". ").append(tops.get(i).get("name_en")).append('\n');
            Map<?, ?> judge =
                    json.readValue(callLlm(prompt.toString(), 0.0, true), new TypeReference<>() {});
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : judge.entrySet())
                if ("main".equals(entry.getValue())) {
                    try {
                        int i = Integer.parseInt(Objects.toString(entry.getKey())) - 1;
                        if (i >= 0 && i < tops.size()) result.add(tops.get(i));
                    } catch (NumberFormatException ignored) {
                    }
                }
            return result;
        } catch (Exception e) {
            return tops;
        }
    }

    private List<String> generateCultureTags(String productName, String country, String category) {
        List<String> pool = profiles.tags(country, category);
        if (pool.isEmpty()) return List.of();
        if (llmConfigured) {
            String prompt =
                    "你是跨境电商文化适配专家，需要为商品生成符合目标市场文化的标签。\n商品名称："
                            + productName
                            + "\n目标国家："
                            + country
                            + "\n商品品类："
                            + category
                            + "\n\n请从以下标签池中选择最适合的3-5个标签：\n"
                            + String.join(", ", pool)
                            + "\n\n请直接输出标签，用逗号分隔，不要其他内容。";
            try {
                String result = callLlm(prompt, 0.1, false);
                List<String> tags =
                        Arrays.stream(result.split("[,，]"))
                                .map(String::trim)
                                .filter(pool::contains)
                                .distinct()
                                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                if (tags.size() < 3)
                    for (String tag : pool)
                        if (!tags.contains(tag) && tags.size() < 3) tags.add(tag);
                if (tags.size() >= 3) return tags;
            } catch (Exception ignored) {
            }
        }
        String lower = productName.toLowerCase(Locale.ROOT);
        List<String> matched =
                pool.stream().filter(t -> lower.contains(t.toLowerCase(Locale.ROOT))).toList();
        if (!matched.isEmpty()) return matched;
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(5, shuffled.size()));
    }

    private String callLlm(String prompt, double temperature, boolean jsonObject) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", llmModel);
        request.put("temperature", temperature);
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        if (jsonObject) request.put("response_format", Map.of("type", "json_object"));
        Map<?, ?> response =
                llm.post().uri("chat/completions").body(request).retrieve().body(Map.class);
        List<?> choices = (List<?>) response.get("choices");
        Map<?, ?> first = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) first.get("message");
        return Objects.toString(message.get("content"), "").trim();
    }

    private Map<String, Object> evaluate(Map<String, Object> user, List<Map<String, Object>> recs) {
        if (recs.isEmpty()) return Map.of("相关性分数", 0.0, "本地化分数", 0.0);
        String category = Objects.toString(user.get("category")),
                country = Objects.toString(user.get("country"));
        Set<String> tagSet = new HashSet<>(stringList(user.get("culture_tags")));
        long matching = recs.stream().filter(p -> category.equals(p.get("category"))).count();
        double local = 0;
        for (Map<String, Object> p : recs) {
            if (country.equals(p.get("country"))) local += 1;
            for (String tag : stringList(p.get("tags"))) if (tagSet.contains(tag)) local += 0.2;
        }
        double relevance = (double) matching / recs.size();
        local /= recs.size() * 1.5;
        return Map.of("相关性分数", round(relevance), "本地化分数", round(local));
    }

    private double tagScore(Map<String, Object> product, String country, List<String> cultureTags) {
        Set<String> productTags = new HashSet<>();
        productTags.addAll(stringList(product.get("tags")));
        long overlap = cultureTags.stream().filter(productTags::contains).count();
        return overlap * (country.equals(product.get("country")) ? 1.2 : 0.7);
    }

    private void recordExposure(List<Map<String, Object>> recs) {
        if (recs.isEmpty()) return;
        try {
            List<Object[]> rows = recs.stream().map(p -> new Object[] {p.get("id"), 1}).toList();
            db.batchUpdate(
                    "insert into biz_rec_stat_log(product_id,action_type,create_time)"
                        + " values(?,?,now())",
                    rows);
        } catch (Exception ignored) {
        }
    }

    private List<Map<String, Object>> fallbackProducts(int topN) {
        try {
            loadProductsWhenNeeded();
            List<Map<String, Object>> copy = new ArrayList<>(products);
            Collections.shuffle(copy);
            return copy.stream()
                    .limit(topN)
                    .map(
                            p -> {
                                Map<String, Object> out = new LinkedHashMap<>();
                                for (String key :
                                        List.of(
                                                "idx",
                                                "id",
                                                "name_en",
                                                "category",
                                                "country",
                                                "tags")) out.put(key, p.get(key));
                                return out;
                            })
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> defaultRecommendations(int topN) {
        return DEFAULT_PRODUCTS.subList(0, Math.min(topN, DEFAULT_PRODUCTS.size()));
    }

    private List<Map<String, Object>> mockProducts() {
        List<Map<String, Object>> out = new ArrayList<>();
        String[][] rows = {
            {"B08XJ8S7GQ", "Smart Bracelet"},
            {"B07K1R3Q2T", "Wireless Earphones"},
            {"B09N2X4Y5Z", "Portable Charger"},
            {"B08M7N6O5P", "Bluetooth Speaker"},
            {"B07C8D9E0F", "Smart Light Bulb"},
            {"B06A1B2C3D", "Sports Water Bottle"},
            {"B05E4F3G2H", "Casual T-Shirt"},
            {"B04I5J6K7L", "Jeans"}
        };
        for (int i = 0; i < rows.length; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("idx", i + 1);
            p.put("id", rows[i][0]);
            p.put("name_en", rows[i][1]);
            p.put("category", i < 5 ? "3c" : "clothes");
            p.put("country", "中国");
            p.put("tags", new ArrayList<String>());
            p.put("image_url", null);
            out.add(p);
        }
        return out;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(Objects::toString).toList();
    }

    private String normalizeProductCategory(Object value) {
        String category = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
        return category.equals("3c") ? "3c" : category.equals("clothes") ? "clothes" : "3c";
    }

    private String formatImageUrl(String value) {
        if (value.isBlank()) return null;
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        return value.startsWith("/") ? imageBaseUrl + value : imageBaseUrl + "/" + value;
    }

    private List<Float> floatVector(List<?> raw) {
        return raw.stream().map(v -> ((Number) v).floatValue()).toList();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Map<String, Object> mock(
            int idx, String id, String name, String category, String country, String... tags) {
        return Map.of(
                "idx",
                idx,
                "id",
                id,
                "name_en",
                name,
                "category",
                category,
                "country",
                country,
                "tags",
                List.of(tags));
    }
}
