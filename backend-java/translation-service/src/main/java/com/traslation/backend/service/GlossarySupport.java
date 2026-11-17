package com.traslation.backend.service;

import com.traslation.backend.common.glossary.GlossaryRecall;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 术语约束的翻译侧入口：读取用户术语库，其余逻辑统一委托给 {@link GlossaryRecall}。
 *
 * <p>之所以只保留取数、把召回/约束块/校验都放到 platform-common：术语召回在 「翻译链路」与「术语库 HTTP 接口」两处都要用，两套实现必然漂移 —— 之前就是 Java
 * 侧自己实现了一份「同品类进参考项」的逻辑，而参考项实际来自向量召回，导致返回结构虽都是 mandatory/optional，语义却不一致。
 */
final class GlossarySupport {

    private GlossarySupport() {}

    /** 读取用户在某语言下的术语（缺少 user_id 时返回空，不做全库召回）。 */
    static List<GlossaryRecall.Term> loadTerms(JdbcTemplate db, Long userId, String language) {
        if (userId == null || language == null || language.isBlank()) return List.of();
        try {
            return db.query(
                    "select id,chinese,`foreign`,category from sys_glossary"
                            + " where user_id=? and language=? order by id",
                    (rs, i) ->
                            GlossaryRecall.Term.of(
                                    rs.getLong("id"),
                                    Objects.toString(rs.getString("chinese"), ""),
                                    Objects.toString(rs.getString("foreign"), ""),
                                    Objects.toString(rs.getString("category"), "general")
                                            .toLowerCase(Locale.ROOT)),
                    userId,
                    language);
        } catch (Exception e) {
            // 术语故障不应阻断翻译：降级为无术语约束，由调用方继续走翻译
            return List.of();
        }
    }

    /** 召回：规模未超阈值时全量注入，超过阈值时才走字面 + 向量混合召回。 */
    static GlossaryRecall.Recalled recall(String text, List<GlossaryRecall.Term> terms) {
        return GlossaryRecall.retrieve(text, terms, null);
    }

    static String constraintBlock(GlossaryRecall.Recalled recalled) {
        return GlossaryRecall.constraintBlock(recalled);
    }

    /** 落地校验报告：checked / violations / compliance_rate / terms_injected / 专有名词缺失。 */
    static Map<String, Object> verify(String translation, GlossaryRecall.Recalled recalled) {
        return GlossaryRecall.verifyDetailed(translation, recalled);
    }
}
