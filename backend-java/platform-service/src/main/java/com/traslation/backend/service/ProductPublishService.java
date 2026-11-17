package com.traslation.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traslation.backend.common.BusinessException;
import com.traslation.backend.common.id.SnowflakeIdGenerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.sql.*;
import java.util.*;

@Service
public class ProductPublishService {
    private static final List<String> LANGS =
            List.of("en", "ar", "fr", "de", "ja", "ko", "ru", "th", "vi", "ms", "id", "tl");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final SnowflakeIdGenerator idGenerator;
    private final RestClient translation;
    private final RestClient catalog;
    private final RecommendationRefreshService refreshService;
    private final BusinessCacheEvictor cache;

    public ProductPublishService(
            JdbcTemplate db,
            ObjectMapper json,
            SnowflakeIdGenerator idGenerator,
            RecommendationRefreshService refreshService,
            BusinessCacheEvictor cache,
            @Value("${app.translation.url:http://localhost:8083}") String translationUrl,
            @Value("${app.catalog.url:http://localhost:8082}") String catalogUrl) {
        this.db = db;
        this.json = json;
        this.idGenerator = idGenerator;
        this.refreshService = refreshService;
        this.cache = cache;
        translation = RestClient.builder().baseUrl(translationUrl).build();
        catalog = RestClient.builder().baseUrl(catalogUrl).build();
    }

    @Transactional
    public Map<String, Object> publish(Map<String, Object> body, long userId, String username) {
        String title = req(body, "title"),
                description = req(body, "description"),
                category = Objects.toString(body.get("category"), "clothes"),
                country = Objects.toString(body.get("target_country"), "沙特");
        List<String> languages =
                values(body.get("target_languages"), "en").stream()
                        .filter(LANGS::contains)
                        .toList();
        if (languages.isEmpty()) languages = List.of("en");
        List<String> platforms = values(body.get("target_platforms"), "amazon");
        // esin 取 uuid4 前 10 位转大写，结果形如 8FDA8F74-B（第 9 位是连字符）。
        // 这里若去掉连字符会变成 10 位纯字符，与历史数据的格式不一致。
        String esin = UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT);
        String categoryCn = PublishSupport.categoryCn(category);
        Map<String, Object> translations = new LinkedHashMap<>(),
                translatedTitles = new HashMap<>();
        String primary = languages.get(0);
        // 只为主目标语言写入标题/描述译文，其余语种列填 ESIN 占位。
        // 这里保持一致：否则同一条数据在两侧的 en_title/ar_title 会不同。
        Map<String, Object> primaryDesc =
                translate(description, primary, categoryCn, country, userId, username);
        Map<String, Object> primaryTitle =
                translate(title, primary, categoryCn, country, userId, username);
        translatedTitles.put(primary, primaryTitle.get("versionA"));
        translations.put(primary, primaryDesc);
        for (String lang : languages) {
            if (lang.equals(primary)) continue;
            translations.put(
                    lang, translate(description, lang, categoryCn, country, userId, username));
        }
        Map<String, Object> compliance = new LinkedHashMap<>();
        for (String platform : platforms) {
            String translated =
                    Objects.toString(
                            ((Map<?, ?>) translations.getOrDefault(primary, Map.of()))
                                    .get("versionA"),
                            description);
            compliance.put(platform, PublishSupport.checkCompliance(translated, platform));
        }
        List<String> columns =
                new ArrayList<>(
                        List.of(
                                "user_id",
                                "esin",
                                "category",
                                "country",
                                "price",
                                "create_time",
                                "zh_title",
                                "zh_desc"));
        List<Object> params =
                new ArrayList<>(
                        List.of(
                                userId,
                                esin,
                                category,
                                country,
                                body.getOrDefault("price", 0),
                                new Timestamp(System.currentTimeMillis()),
                                title,
                                description));
        String primaryTitleText = Objects.toString(translatedTitles.get(primary), esin);
        String primaryDescText =
                Objects.toString(
                        ((Map<?, ?>) translations.getOrDefault(primary, Map.of())).get("versionA"),
                        esin);
        for (String lang : LANGS) {
            boolean isPrimary = lang.equals(primary);
            columns.add(lang + "_title");
            columns.add(lang + "_desc");
            params.add(isPrimary ? primaryTitleText : esin);
            params.add(isPrimary ? primaryDescText : esin);
        }
        // product.id 已去掉 AUTO_INCREMENT，主键由雪花算法预先生成再显式插入。
        // 好处是插入前后都不需要回读自增值，也避免依赖数据库连接的自增语义。
        long productId = idGenerator.nextId();
        columns.add(0, "id");
        params.add(0, productId);
        String marks = String.join(",", Collections.nCopies(columns.size(), "?"));
        String sql = "insert into product(" + String.join(",", columns) + ") values(" + marks + ")";
        db.update(sql, params.toArray());
        Object imageValue = body.get("images");
        if (imageValue instanceof Collection<?> images) {
            int sort = 0;
            for (Object image : images) {
                String url = Objects.toString(image, "").trim();
                if (!url.isEmpty())
                    db.update(
                            "insert into product_image(id,product_id,image_url,sort,create_time)"
                                    + " values(?,?,?,?,now())",
                            idGenerator.nextId(),
                            productId,
                            url,
                            sort++);
            }
        }
        String countries = write(List.of(country)), langs = write(languages);
        db.update(
                "insert into publish_record(id,user_id,product_id,publish_date,target_countries,"
                    + "target_languages,translations,compliance_results,recommendation_data,status)"
                    + " values(?,?,?,now(),?,?,?,?,?,'published')",
                idGenerator.nextId(),
                userId,
                esin,
                countries,
                langs,
                write(translations),
                write(compliance),
                "{}");
        String keywords = Objects.toString(body.get("keywords"), "");
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // 缓存失效必须放在提交之后：放在事务内会出现「缓存已清、事务回滚」的脏读窗口
                        cache.evictProduct(esin, userId);
                        try {
                            catalog.post()
                                    .uri("/internal/search/products/{esin}", esin)
                                    .retrieve()
                                    .toBodilessEntity();
                        } catch (Exception ignored) {
                        }
                        refreshService.refresh(esin, userId, country, category, keywords);
                    }
                });
        return Map.of(
                "product_id",
                esin,
                "product_db_id",
                productId,
                "translations",
                translations,
                "compliance",
                compliance,
                "recommendation",
                Map.of("status", "processing", "message", "推荐正在生成中，稍后可在商品详情中查看"));
    }

    private Map<String, Object> translate(
            String text,
            String lang,
            String category,
            String country,
            long userId,
            String username) {
        try {
            Map<?, ?> envelope =
                    translation
                            .post()
                            .uri("/api/translate")
                            .header("X-User-Id", Long.toString(userId))
                            .header("X-User-Name", username)
                            .body(
                                    Map.of(
                                            "text",
                                            text,
                                            "target_lang",
                                            lang,
                                            "category",
                                            category,
                                            "country",
                                            country))
                            .retrieve()
                            .body(Map.class);
            Object data = envelope == null ? null : envelope.get("data");
            if (data instanceof Map<?, ?> m) {
                Map<String, Object> result = new LinkedHashMap<>();
                m.forEach((k, v) -> result.put(Objects.toString(k), v));
                return result;
            }
        } catch (Exception ignored) {
        }
        String fallback = "[Translated to " + lang + "]: " + text;
        return new LinkedHashMap<>(
                Map.of(
                        "versionA",
                        fallback,
                        "versionB",
                        fallback,
                        "core_selling_points",
                        List.of(),
                        "cultural_notes",
                        "",
                        "seo_keywords",
                        List.of(),
                        "degraded",
                        true));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON序列化失败", e);
        }
    }

    private String req(Map<String, Object> b, String key) {
        String value = Objects.toString(b.get(key), "").trim();
        if (value.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, key + "不能为空");
        return value;
    }

    private List<String> values(Object raw, String fallback) {
        List<String> result;
        if (raw == null) result = List.of();
        else if (raw instanceof Collection<?> c)
            result = c.stream().map(Objects::toString).filter(s -> !s.isBlank()).toList();
        else
            result =
                    Arrays.stream(raw.toString().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .toList();
        return result.isEmpty() ? List.of(fallback) : result;
    }
}
