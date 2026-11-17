package com.traslation.backend.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/** Elasticsearch product index access with an optional MySQL fallback at callers. */
@Service
public class ProductSearchIndex {
    private static final List<String> LANGS =
            List.of("zh", "en", "ar", "fr", "de", "ja", "ko", "ru", "th", "vi", "ms", "id", "tl");
    private final RestClient es;
    private final JdbcTemplate db;
    private final String index, esUrl;
    private final boolean enabled, useForBrowse;

    public ProductSearchIndex(
            RestClient.Builder builder,
            JdbcTemplate db,
            @Value("${app.search.url:http://localhost:9200}") String url,
            @Value("${app.search.index:product}") String index,
            @Value("${app.search.username:}") String username,
            @Value("${app.search.password:}") String password,
            @Value("${app.search.enabled:true}") boolean enabled,
            @Value("${app.search.use-for-browse:false}") boolean useForBrowse) {
        this.es =
                builder.baseUrl(url)
                        .defaultHeaders(
                                h -> {
                                    if (username != null && !username.isBlank())
                                        h.setBasicAuth(username, password == null ? "" : password);
                                })
                        .build();
        this.db = db;
        this.index = index;
        this.esUrl = url;
        this.enabled = enabled;
        this.useForBrowse = useForBrowse;
    }

    public boolean available() {
        if (!enabled) return false;
        try {
            return es.get()
                    .uri("/")
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean useForBrowse() {
        return useForBrowse;
    }

    public Map<String, Object> elasticsearchHealth() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<?, ?> health = es.get().uri("/_cluster/health").retrieve().body(Map.class);
            Map<?, ?> root = es.get().uri("/").retrieve().body(Map.class);
            Object v = root == null ? null : root.get("version");
            Map<?, ?> version = v instanceof Map<?, ?> m ? m : Map.of();
            out.put("available", true);
            out.put("cluster_name", health.get("cluster_name"));
            out.put("status", health.get("status"));
            out.put("number_of_nodes", health.get("number_of_nodes"));
            out.put("version", version.get("number"));
        } catch (Exception e) {
            out.put("available", false);
            out.put("error", Objects.toString(e.getMessage(), "Elasticsearch unavailable"));
        }
        return out;
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean healthy = available();
        out.put("configured_backend", "auto");
        out.put("es_available", healthy);
        out.put("es_hosts", List.of(esUrl));
        out.put("index", index);
        Object docs = null;
        Integer mysql = null;
        try {
            // 只回归一化的四个字段，不回 ES 原始根信息
            Map<?, ?> health = es.get().uri("/_cluster/health").retrieve().body(Map.class);
            Map<?, ?> root = es.get().uri("/").retrieve().body(Map.class);
            Map<?, ?> version =
                    root != null && root.get("version") instanceof Map<?, ?> v ? v : Map.of();
            Map<String, Object> cluster = new LinkedHashMap<>();
            cluster.put("cluster_name", health == null ? null : health.get("cluster_name"));
            cluster.put("status", health == null ? null : health.get("status"));
            cluster.put("number_of_nodes", health == null ? null : health.get("number_of_nodes"));
            cluster.put("version", version.get("number"));
            out.put("cluster", cluster);
            Map<?, ?> count = es.get().uri("/{index}/_count", index).retrieve().body(Map.class);
            docs = count == null ? null : count.get("count");
        } catch (Exception e) {
            out.put(
                    "cluster",
                    Map.of("error", Objects.toString(e.getMessage(), "Elasticsearch unavailable")));
        }
        out.put("index_exists", docs != null);
        out.put("index_doc_count", docs);
        try {
            mysql = db.queryForObject("select count(*) from product", Integer.class);
        } catch (Exception ignored) {
        }
        out.put("mysql_product_count", mysql);
        out.put(
                "in_sync",
                docs instanceof Number d && mysql != null && d.longValue() == mysql.longValue());
        return out;
    }

    public List<String> suggest(String keyword, int size) {
        if (!available() || keyword == null || keyword.isBlank()) return List.of();
        int limit = Math.min(20, Math.max(1, size));
        List<String> result = new ArrayList<>();
        try {
            Map<String, Object> request =
                    Map.of(
                            "suggest",
                            Map.of(
                                    "product_suggest",
                                    Map.of(
                                            "prefix",
                                            keyword,
                                            "completion",
                                            Map.of(
                                                    "field",
                                                    "suggest",
                                                    "size",
                                                    limit,
                                                    "skip_duplicates",
                                                    true))),
                            "_source",
                            false);
            Map<?, ?> response =
                    es.post()
                            .uri("/{index}/_search", index)
                            .body(request)
                            .retrieve()
                            .body(Map.class);
            Object groups = ((Map<?, ?>) response.get("suggest")).get("product_suggest");
            if (groups instanceof List<?> list)
                for (Object group : list) {
                    Object options = ((Map<?, ?>) group).get("options");
                    if (options instanceof List<?> opts)
                        for (Object option : opts) {
                            String value = Objects.toString(((Map<?, ?>) option).get("text"), "");
                            if (!value.isBlank() && !result.contains(value)) result.add(value);
                        }
                }
        } catch (Exception ignored) {
        }
        if (result.size() < limit)
            try {
                Map<String, Object> request =
                        Map.of(
                                "size",
                                limit * 2,
                                "_source",
                                List.of("zh_title", "en_title"),
                                "query",
                                Map.of(
                                        "multi_match",
                                        Map.of(
                                                "query",
                                                keyword,
                                                "fields",
                                                List.of("zh_title^4", "en_title^3"),
                                                "type",
                                                "phrase_prefix")));
                Map<?, ?> response =
                        es.post()
                                .uri("/{index}/_search", index)
                                .body(request)
                                .retrieve()
                                .body(Map.class);
                Object hits = ((Map<?, ?>) response.get("hits")).get("hits");
                if (hits instanceof List<?> list)
                    for (Object h : list) {
                        Map<?, ?> src = (Map<?, ?>) ((Map<?, ?>) h).get("_source");
                        String value = Objects.toString(src.get("zh_title"), "").trim();
                        if (value.isBlank())
                            value = Objects.toString(src.get("en_title"), "").trim();
                        if (value.length() > 40) value = value.substring(0, 40);
                        if (!value.isBlank() && !result.contains(value)) result.add(value);
                        if (result.size() >= limit) break;
                    }
            } catch (Exception ignored) {
            }
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    /** 原始命中，交给 service 层统一格式化，避免两套字段结构。 */
    public record EsHit(Map<String, Object> source, Object score, Object highlight) {}

    /** 检索结果：hit 列表 + 总数。 */
    public record EsResult(List<EsHit> hits, int total) {}

    /**
     * 只返回原始命中，不做字段映射。
     *
     * <p>ES 与 MySQL 两条路径必须由同一套格式化逻辑产出响应，否则会出现 「列表有 13 语种字段、搜索只有当前语言」这类不一致。
     */
    public EsResult searchHits(
            String q, int page, int size, String language, String category, String country) {
        if (!available()) return null;
        try {
            List<Object> filters = new ArrayList<>();
            if (category != null && !category.isBlank() && !category.equals("all"))
                filters.add(Map.of("term", Map.of("category", category)));
            if (country != null && !country.isBlank())
                filters.add(Map.of("term", Map.of("country", country)));
            Map<String, Object> query;
            if (q == null || q.isBlank())
                query =
                        Map.of(
                                "bool",
                                Map.of(
                                        "must",
                                        List.of(Map.of("match_all", Map.of())),
                                        "filter",
                                        filters));
            else
                query = Map.of("bool", Map.of("must", List.of(keywordQuery(q)), "filter", filters));

            int n = Math.min(100, Math.max(1, size));
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("from", (Math.max(1, page) - 1) * n);
            request.put("size", n);
            request.put("track_total_hits", true);
            request.put(
                    "sort",
                    q == null || q.isBlank()
                            ? List.of(Map.of("create_time", Map.of("order", "desc")))
                            : List.of("_score", Map.of("create_time", Map.of("order", "desc"))));
            if (q != null && !q.isBlank()) {
                Map<String, Object> highlightFields = new LinkedHashMap<>();
                for (String lang : LANGS) {
                    highlightFields.put(lang + "_title", Map.of("number_of_fragments", 0));
                    highlightFields.put(
                            lang + "_desc", Map.of("fragment_size", 120, "number_of_fragments", 1));
                }
                request.put(
                        "highlight",
                        Map.of(
                                "pre_tags",
                                List.of("<em>"),
                                "post_tags",
                                List.of("</em>"),
                                "fields",
                                highlightFields));
            }

            Map<?, ?> response =
                    es.post()
                            .uri("/{index}/_search", index)
                            .body(request)
                            .retrieve()
                            .body(Map.class);
            Map<?, ?> hits = (Map<?, ?>) response.get("hits");
            int count = ((Number) ((Map<?, ?>) hits.get("total")).get("value")).intValue();
            List<EsHit> out = new ArrayList<>();
            Object rows = hits.get("hits");
            if (rows instanceof List<?> list)
                for (Object hit : list) {
                    Map<?, ?> h = (Map<?, ?>) hit;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> source =
                            h.get("_source") == null
                                    ? new LinkedHashMap<>()
                                    : new LinkedHashMap<>((Map<String, Object>) h.get("_source"));
                    out.add(new EsHit(source, h.get("_score"), h.get("highlight")));
                }
            return new EsResult(out, count);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> search(
            String q, int page, int size, String language, String category, String country) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!available()) return null;
        try {
            List<Object> filters = new ArrayList<>();
            if (category != null && !category.isBlank() && !category.equals("all"))
                filters.add(Map.of("term", Map.of("category", category)));
            if (country != null && !country.isBlank())
                filters.add(Map.of("term", Map.of("country", country)));
            Map<String, Object> query;
            if (q == null || q.isBlank())
                query =
                        Map.of(
                                "bool",
                                Map.of(
                                        "must",
                                        List.of(Map.of("match_all", Map.of())),
                                        "filter",
                                        filters));
            else
                query = Map.of("bool", Map.of("must", List.of(keywordQuery(q)), "filter", filters));
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("from", (Math.max(1, page) - 1) * Math.min(100, Math.max(1, size)));
            request.put("size", Math.min(100, Math.max(1, size)));
            request.put("track_total_hits", true);
            request.put(
                    "sort",
                    q == null || q.isBlank()
                            ? List.of(Map.of("create_time", Map.of("order", "desc")))
                            : List.of("_score", Map.of("create_time", Map.of("order", "desc"))));
            if (q != null && !q.isBlank()) {
                Map<String, Object> highlightFields = new LinkedHashMap<>();
                for (String lang : LANGS) {
                    highlightFields.put(lang + "_title", Map.of("number_of_fragments", 0));
                    highlightFields.put(
                            lang + "_desc", Map.of("fragment_size", 120, "number_of_fragments", 1));
                }
                request.put(
                        "highlight",
                        Map.of(
                                "pre_tags",
                                List.of("<em>"),
                                "post_tags",
                                List.of("</em>"),
                                "fields",
                                highlightFields));
            }
            Map<?, ?> response =
                    es.post()
                            .uri("/{index}/_search", index)
                            .body(request)
                            .retrieve()
                            .body(Map.class);
            Map<?, ?> hits = (Map<?, ?>) response.get("hits");
            Map<?, ?> total = (Map<?, ?>) hits.get("total");
            int count = ((Number) total.get("value")).intValue();
            List<Map<String, Object>> products = new ArrayList<>();
            Object rows = hits.get("hits");
            if (rows instanceof List<?> list)
                for (Object hit : list) {
                    Map<?, ?> source = (Map<?, ?>) ((Map<?, ?>) hit).get("_source");
                    String lang = LANGS.contains(language) ? language : "zh";
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("id", source.get("id"));
                    p.put("esin", source.get("esin"));
                    p.put("category", source.get("category"));
                    p.put("title", source.get(lang + "_title"));
                    p.put("description", source.get(lang + "_desc"));
                    p.put("country", source.get("country"));
                    p.put("price", source.get("price"));
                    p.put("user_id", source.get("user_id"));
                    p.put("create_time", source.get("create_time"));
                    p.put("image_url", source.get("image_url"));
                    p.put(
                            "image_urls",
                            source.get("image_url") == null
                                    ? List.of()
                                    : List.of(source.get("image_url")));
                    p.put("foreign_title", source.get(lang + "_title"));
                    p.put("foreign_desc", source.get(lang + "_desc"));
                    p.put("score", ((Map<?, ?>) hit).get("_score"));
                    if (((Map<?, ?>) hit).containsKey("highlight"))
                        p.put("highlight", ((Map<?, ?>) hit).get("highlight"));
                    products.add(p);
                }
            out.put("products", products);
            out.put("total", count);
            out.put("current", Math.max(1, page));
            out.put("per_page", Math.min(100, Math.max(1, size)));
            out.put(
                    "pages",
                    count == 0
                            ? 0
                            : (int) Math.ceil((double) count / Math.min(100, Math.max(1, size))));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean indexProduct(String esin) {
        if (!available()) return false;
        try {
            ensureIndex();
            Map<String, Object> row =
                    db.queryForMap(
                            "select p.*, (select image_url from product_image pi where"
                                    + " pi.product_id=p.id order by pi.sort,pi.id limit 1)"
                                    + " primary_image from product p where p.esin=?",
                            esin);
            Map<String, Object> doc = new LinkedHashMap<>();
            for (String lang : LANGS) {
                doc.put(lang + "_title", row.get(lang + "_title"));
                doc.put(lang + "_desc", row.get(lang + "_desc"));
            }
            for (String key :
                    List.of(
                            "id",
                            "esin",
                            "user_id",
                            "category",
                            "country",
                            "source_file",
                            "price",
                            "create_time")) doc.put(key, row.get(key));
            doc.put("image_url", row.get("primary_image"));
            List<String> suggestions = new ArrayList<>();
            for (String key : List.of("zh_title", "en_title", "esin")) {
                Object v = row.get(key);
                if (v != null && !v.toString().isBlank()) suggestions.add(v.toString());
            }
            doc.put("suggest", Map.of("input", suggestions));
            es.put().uri("/{index}/_doc/{id}", index, esin).body(doc).retrieve().toBodilessEntity();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean deleteProduct(String esin) {
        if (!available()) return false;
        try {
            es.delete().uri("/{index}/_doc/{id}", index, esin).retrieve().toBodilessEntity();
            return true;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            return e.getStatusCode().value() == 404;
        } catch (Exception ignored) {
            return false;
        }
    }

    public Map<String, Object> rebuild() {
        if (!available()) return Map.of("ok", false, "error", "Elasticsearch 不可用");
        try {
            try {
                es.delete().uri("/{index}", index).retrieve().toBodilessEntity();
            } catch (Exception ignored) {
            }
            ensureIndex();
            List<Map<String, Object>> rows =
                    db.queryForList("select esin from product order by id");
            int done = 0, failed = 0;
            for (Map<String, Object> row : rows) {
                if (indexProduct(Objects.toString(row.get("esin")))) done++;
                else failed++;
            }
            return Map.of("ok", failed == 0, "indexed", done, "failed", failed);
        } catch (Exception e) {
            return Map.of("ok", false, "error", Objects.toString(e.getMessage(), "rebuild failed"));
        }
    }

    public void ensureIndex() {
        if (!available()) return;
        try {
            es.get().uri("/{index}", index).retrieve().toBodilessEntity();
            return;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) throw e;
        } catch (Exception ignored) {
        }
        String zhIndex = "cjk", zhSearch = "cjk";
        try {
            es.post()
                    .uri("/_analyze")
                    .body(Map.of("analyzer", "ik_max_word", "text", "纯棉透气连衣裙"))
                    .retrieve()
                    .toBodilessEntity();
            zhIndex = "ik_max_word";
            zhSearch = "ik_smart";
        } catch (Exception ignored) {
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(
                "all_text",
                Map.of("type", "text", "analyzer", zhIndex, "search_analyzer", zhSearch));
        props.put(
                "suggest",
                Map.of(
                        "type",
                        "completion",
                        "analyzer",
                        zhSearch,
                        "preserve_separators",
                        true,
                        "preserve_position_increments",
                        true,
                        "max_input_length",
                        80));
        for (String f : List.of("esin", "category", "country", "source_file"))
            props.put(f, Map.of("type", "keyword"));
        for (String f : List.of("id", "user_id")) props.put(f, Map.of("type", "long"));
        props.put("price", Map.of("type", "scaled_float", "scaling_factor", 100));
        props.put("image_url", Map.of("type", "keyword", "index", false));
        props.put(
                "create_time",
                Map.of("type", "date", "format", "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"));
        for (String lang : LANGS) {
            String
                    analyzer =
                            lang.equals("zh")
                                    ? zhIndex
                                    : lang.equals("en") ? "english" : "standard",
                    searchAnalyzer = lang.equals("zh") ? zhSearch : analyzer;
            Map<String, Object> title =
                    new LinkedHashMap<>(
                            Map.of(
                                    "type",
                                    "text",
                                    "analyzer",
                                    analyzer,
                                    "search_analyzer",
                                    searchAnalyzer,
                                    "copy_to",
                                    "all_text",
                                    "fields",
                                    Map.of("raw", Map.of("type", "keyword", "ignore_above", 300))));
            Map<String, Object> desc =
                    new LinkedHashMap<>(
                            Map.of(
                                    "type",
                                    "text",
                                    "analyzer",
                                    analyzer,
                                    "search_analyzer",
                                    searchAnalyzer,
                                    "copy_to",
                                    "all_text"));
            props.put(lang + "_title", title);
            props.put(lang + "_desc", desc);
        }
        Map<String, Object> body =
                Map.of(
                        "settings",
                        Map.of(
                                "number_of_shards",
                                1,
                                "number_of_replicas",
                                0,
                                "refresh_interval",
                                "1s",
                                "max_result_window",
                                20000),
                        "mappings",
                        Map.of("dynamic", "strict", "properties", props));
        es.put().uri("/{index}", index).body(body).retrieve().toBodilessEntity();
    }

    private Map<String, Object> keywordQuery(String q) {
        List<String> titles = new ArrayList<>();
        for (String lang : LANGS)
            titles.add(
                    lang + "_title^" + (lang.equals("zh") ? "4" : lang.equals("en") ? "3" : "1.5"));
        titles.add("zh_title.raw^6");
        List<String> fields = new ArrayList<>(titles);
        for (String lang : LANGS) fields.add(lang + "_desc^0.8");
        fields.add("all_text");
        if (q.length() < 2)
            return Map.of(
                    "multi_match",
                    Map.of("query", q, "fields", titles, "type", "best_fields", "operator", "or"));
        String required = q;
        try {
            Map<?, ?> a =
                    es.post()
                            .uri("/{index}/_analyze", index)
                            .body(Map.of("field", "all_text", "text", q))
                            .retrieve()
                            .body(Map.class);
            Object tokens = a.get("tokens");
            if (tokens instanceof List<?> list) {
                String longest = "";
                for (Object t : list) {
                    String value = Objects.toString(((Map<?, ?>) t).get("token"), "");
                    if (value.length() > longest.length()) longest = value;
                }
                if (!longest.isBlank()) required = longest;
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> must =
                Map.of(
                        "multi_match",
                        Map.of("query", required, "fields", fields, "type", "phrase"));
        Map<String, Object> should =
                Map.of(
                        "multi_match",
                        Map.of(
                                "query",
                                q,
                                "fields",
                                fields,
                                "type",
                                "best_fields",
                                "tie_breaker",
                                0.3,
                                "operator",
                                "or",
                                "minimum_should_match",
                                "50%"));
        return Map.of(
                "bool",
                Map.of(
                        "must",
                        List.of(must),
                        "should",
                        List.of(should),
                        "minimum_should_match",
                        0));
    }

    private List<String> searchFields() {
        List<String> fields = new ArrayList<>();
        for (String lang : LANGS) {
            fields.add(
                    lang + "_title^" + (lang.equals("zh") ? "4" : lang.equals("en") ? "3" : "1.5"));
            fields.add(lang + "_desc^0.8");
        }
        fields.add("all_text");
        return fields;
    }
}
