package com.traslation.backend.api;

import com.traslation.backend.common.glossary.GlossaryRecall;
import com.traslation.backend.common.id.SnowflakeIdGenerator;
import com.traslation.backend.service.BusinessCacheEvictor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 术语库接口。
 *
 * <p>两点与 Flask 保持严格一致，否则前端行为会变：
 *
 * <ul>
 *   <li><b>成功/失败都返回 HTTP 200</b>，业务状态放在 body 的 {@code code} 里。 Flask 全部走 {@code
 *       jsonify({...})}，校验失败也是 200 + code=400， 前端 axios 不会进 reject 分支。
 *   <li><b>字段名用 {@code foreign}</b>。{@code foreign} 是 MySQL 保留字，SQL 里必须加反引号，
 *       一旦漏掉会静默查不到数据（异常被上层兜住时尤其难查）。
 * </ul>
 *
 * <p>召回逻辑统一走 {@link GlossaryRecall}：翻译链路与这里共用同一份实现， 避免出现「翻译用一套、接口用另一套」的口径漂移。
 */
@RestController
@RequestMapping("/api/glossary")
public class GlossaryController {
    /** 与 sys_glossary 列一一对应，供「写入后回读整行」使用。 */
    private static final String COLUMNS =
            "id,user_id,chinese,`foreign`,language,category,note,"
                    + "date_format(create_time,'%Y-%m-%d %H:%i:%s') create_time,"
                    + "date_format(update_time,'%Y-%m-%d %H:%i:%s') update_time";

    private static final String APPLY_MESSAGE =
            "该接口已从「字符串替换」改为「约束注入 + 落地校验」；返回的 text 为原文未替换结果，"
                    + "请使用 constraint_block 注入 LLM prompt。";

    private final JdbcTemplate db;
    private final BusinessCacheEvictor cache;
    private final RestClient embedding;
    private final SnowflakeIdGenerator idGenerator;

    public GlossaryController(
            JdbcTemplate db,
            BusinessCacheEvictor cache,
            SnowflakeIdGenerator idGenerator,
            @Value("${app.embedding.base-url:http://localhost:10683}") String embeddingUrl) {
        this.db = db;
        this.cache = cache;
        this.idGenerator = idGenerator;
        this.embedding = RestClient.builder().baseUrl(embeddingUrl).build();
    }

    // ==================== 增删改查 ====================

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language) {
        Long uid = uidOrNull(userId);
        if (uid == null) return error(401, "未登录");

        StringBuilder sql =
                new StringBuilder("select " + COLUMNS + " from sys_glossary where user_id=?");
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (search != null && !search.isBlank()) {
            sql.append(" and (chinese like ? or `foreign` like ?)");
            String like = "%" + search + "%";
            args.add(like);
            args.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" and category=?");
            args.add(category);
        }
        if (language != null && !language.isBlank()) {
            sql.append(" and language=?");
            args.add(language);
        }
        // Flask 用 query.all()，实际按主键升序；用 create_time 排序会让列表顺序整体相反
        sql.append(" order by id");
        return ok(db.queryForList(sql.toString(), args.toArray()));
    }

    @PostMapping("/add")
    @Transactional
    public Map<String, Object> add(
            @RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        Long uid = uidOrNull(userId);
        if (uid == null) return error(401, "未登录");
        String chinese = text(body.get("chinese")), foreign = text(body.get("foreign"));
        if (chinese.isBlank() || foreign.isBlank()) return error(400, "中文术语和外文译文不能为空");
        String language = defaultIfBlank(text(body.get("language")), "en"),
                category = defaultIfBlank(text(body.get("category")), "all"),
                note = text(body.get("note"));

        if (exists(uid, chinese, language, -1)) return error(500, "添加失败");
        Long id;
        try {
            id = insert(uid, chinese, foreign, language, category, note);
        } catch (DuplicateKeyException e) {
            return error(500, "添加失败");
        }
        cache.evictGlossary(uid, language);
        Map<String, Object> out = ok(findById(id, uid));
        out.put("msg", "添加成功");
        return out;
    }

    @PostMapping("/update")
    @Transactional
    public Map<String, Object> update(
            @RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        Long uid = uidOrNull(userId);
        if (uid == null) return error(401, "未登录");
        Long id = longOrNull(body.get("id"));
        if (id == null) return error(400, "术语ID、中文术语和外文译文不能为空");
        String chinese = text(body.get("chinese")), foreign = text(body.get("foreign"));
        if (chinese.isBlank() || foreign.isBlank()) return error(400, "术语ID、中文术语和外文译文不能为空");
        String language = defaultIfBlank(text(body.get("language")), "en"),
                category = defaultIfBlank(text(body.get("category")), "all"),
                note = text(body.get("note"));

        Map<String, Object> existing = findById(id, uid);
        if (existing == null) return error(500, "更新失败");
        // 改了中文或语言才需要查重，与 Flask 的条件一致
        boolean changed =
                !chinese.equals(text(existing.get("chinese")))
                        || !language.equals(text(existing.get("language")));
        if (changed && exists(uid, chinese, language, id)) return error(500, "更新失败");

        int rows =
                db.update(
                        "update sys_glossary set chinese=?,`foreign`=?,language=?,category=?,note=?"
                                + " where id=? and user_id=?",
                        chinese,
                        foreign,
                        language,
                        category,
                        note,
                        id,
                        uid);
        if (rows == 0) return error(500, "更新失败");
        cache.evictGlossary(uid, language);
        // 语言被改动时，旧语言的缓存也要失效
        String previous = text(existing.get("language"));
        if (!previous.isBlank() && !previous.equals(language)) cache.evictGlossary(uid, previous);
        Map<String, Object> out = ok(findById(id, uid));
        out.put("msg", "更新成功");
        return out;
    }

    @PostMapping("/delete")
    @Transactional
    public Map<String, Object> delete(
            @RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        Long uid = uidOrNull(userId);
        if (uid == null) return error(401, "未登录");
        Long id = longOrNull(body.get("id"));
        if (id == null) return error(400, "术语ID不能为空");

        Map<String, Object> existing = findById(id, uid);
        if (existing == null) return error(500, "删除失败");
        db.update("delete from sys_glossary where id=? and user_id=?", id, uid);
        // Flask 删除时不带 language，只清全部语言聚合键；这里把具体语言键也一并清掉更稳妥
        cache.evictGlossary(uid, text(existing.get("language")));
        return new LinkedHashMap<>(Map.of("code", 200, "msg", "删除成功"));
    }

    // ==================== 召回 ====================

    @PostMapping("/match")
    public Map<String, Object> match(
            @RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        String text = text(body.get("text"));
        if (text.isBlank()) return error(400, "文本不能为空");
        Long uid = uidOrNull(userId);
        if (uid == null) return error(400, "用户ID格式不正确");
        String language = targetLanguage(body);

        GlossaryRecall.Recalled recalled = recall(uid, text, language);
        List<Map<String, Object>> mandatory = dicts(recalled.mandatory());
        List<Map<String, Object>> optional = dicts(recalled.optional());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mandatory", mandatory);
        data.put("optional", optional);
        // 兼容旧字段命名：matched 只包含强制项
        data.put("matched", legacyMatched(recalled.mandatory()));
        data.put("total", mandatory.size() + optional.size());
        return ok(data);
    }

    @PostMapping("/apply")
    public Map<String, Object> apply(
            @RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        String text = text(body.get("text"));
        if (text.isBlank()) return error(400, "文本不能为空");
        Long uid = uidOrNull(userId);
        if (uid == null) return error(400, "用户ID格式不正确");
        String language = targetLanguage(body);

        GlossaryRecall.Recalled recalled = recall(uid, text, language);
        Object report = null;
        String translation = text(body.get("translation"));
        if (!translation.isBlank()) report = GlossaryRecall.verifyDetailed(translation, recalled);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("constraint_block", GlossaryRecall.constraintBlock(recalled));
        data.put("mandatory", dicts(recalled.mandatory()));
        data.put("optional", dicts(recalled.optional()));
        data.put("report", report);
        // 兼容旧字段：原样返回，本接口不再做任何文本替换
        data.put("text", text);
        data.put("deprecated", true);
        data.put("message", APPLY_MESSAGE);
        return ok(data);
    }

    // ==================== 内部实现 ====================

    private GlossaryRecall.Recalled recall(long userId, String text, String language) {
        List<GlossaryRecall.Term> terms = loadTerms(userId, language);
        return GlossaryRecall.retrieve(text, terms, semanticHits(terms, text));
    }

    private List<GlossaryRecall.Term> loadTerms(long userId, String language) {
        try {
            return db.query(
                    "select id,chinese,`foreign`,category from sys_glossary"
                            + " where user_id=? and language=? order by id",
                    (rs, i) ->
                            GlossaryRecall.Term.of(
                                    rs.getLong("id"),
                                    Objects.toString(rs.getString("chinese"), ""),
                                    Objects.toString(rs.getString("foreign"), ""),
                                    Objects.toString(rs.getString("category"), "general")),
                    userId,
                    language);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 向量语义召回，仅在术语库规模超过全量注入阈值时才值得做。
     *
     * <p>短术语的向量表示信息量很低（实测 0.45~0.55 区间基本是噪声）， 所以这里只作为 optional 补充，不能代替字面匹配。
     */
    private List<GlossaryRecall.Term> semanticHits(List<GlossaryRecall.Term> terms, String text) {
        if (terms.size() <= GlossaryRecall.SCALE_THRESHOLD_FULL_INJECT || text.isBlank())
            return List.of();
        try {
            Map<?, ?> response =
                    embedding
                            .post()
                            .uri("/similarity")
                            .body(
                                    Map.of(
                                            "query",
                                            text,
                                            "candidates",
                                            terms.stream().map(GlossaryRecall.Term::zh).toList(),
                                            "top_k",
                                            8))
                            .retrieve()
                            .body(Map.class);
            if (response == null || !(response.get("scores") instanceof List<?> scores))
                return List.of();
            List<GlossaryRecall.Term> out = new ArrayList<>();
            for (int i = 0; i < scores.size() && i < terms.size(); i++) {
                if (!(scores.get(i) instanceof Number number)) continue;
                double score = number.doubleValue();
                if (score < GlossaryRecall.SEMANTIC_MIN_SCORE) continue;
                GlossaryRecall.Term term = terms.get(i);
                out.add(
                        GlossaryRecall.Term.semantic(
                                term.zh(), term.foreign(), term.termType(), score));
            }
            return out;
        } catch (Exception e) {
            // 向量服务不可用时降级为纯字面召回，与 Flask 的懒加载降级一致
            return List.of();
        }
    }

    private List<Map<String, Object>> dicts(List<GlossaryRecall.Term> terms) {
        return terms.stream().map(GlossaryRecall.Term::toDict).toList();
    }

    private List<Map<String, Object>> legacyMatched(List<GlossaryRecall.Term> terms) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GlossaryRecall.Term term : terms) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chinese", term.zh());
            row.put("foreign", term.foreign());
            row.put("category", term.termType());
            row.put("source", term.source());
            out.add(row);
        }
        return out;
    }

    private boolean exists(long userId, String chinese, String language, long excludeId) {
        Integer n =
                db.queryForObject(
                        "select count(*) from sys_glossary where user_id=? and chinese=?"
                                + " and language=? and id<>?",
                        Integer.class,
                        userId,
                        chinese,
                        language,
                        excludeId);
        return n != null && n > 0;
    }

    private Long insert(
            long userId,
            String chinese,
            String foreign,
            String language,
            String category,
            String note) {
        // sys_glossary.id 已去掉 AUTO_INCREMENT，主键由雪花算法生成后显式插入，
        // 不再需要 RETURN_GENERATED_KEYS 回读。
        long id = idGenerator.nextId();
        db.update(
                "insert into sys_glossary(id,user_id,chinese,`foreign`,language,category,note)"
                        + " values(?,?,?,?,?,?,?)",
                id,
                userId,
                chinese,
                foreign,
                language,
                category,
                note);
        return id;
    }

    private Map<String, Object> findById(Long id, long userId) {
        if (id == null) return null;
        List<Map<String, Object>> rows =
                db.queryForList(
                        "select " + COLUMNS + " from sys_glossary where id=? and user_id=?",
                        id,
                        userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String targetLanguage(Map<String, Object> body) {
        String language = text(body.get("target_language"));
        if (language.isBlank()) language = text(body.get("language"));
        return language.isBlank() ? "en" : language;
    }

    private static Map<String, Object> ok(Object data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", 200);
        out.put("data", data);
        return out;
    }

    private static Map<String, Object> error(int code, String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("msg", msg);
        return out;
    }

    private static String text(Object value) {
        return value == null ? "" : Objects.toString(value, "").trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Long longOrNull(Object value) {
        try {
            String raw = text(value);
            return raw.isEmpty() ? null : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long uidOrNull(String value) {
        return longOrNull(value);
    }
}
