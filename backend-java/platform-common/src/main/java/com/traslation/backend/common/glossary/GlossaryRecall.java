package com.traslation.backend.common.glossary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 术语召回：同义词归一 → 字面匹配（长词优先 + 区间屏蔽） → 规模判定 → 约束块 / 落地校验。
 *
 * <p>与 Flask 的 {@code glossary_rag_service} 保持同一策略，关键设计点：
 *
 * <ul>
 *   <li><b>不做后置字符串替换</b>：中文源 → 外文目标方向下，译文里已无中文术语，替换没有可定位目标； 强行替换会产出「100%
 *       cottonbreathable连衣裙」这类粘连结果，阿拉伯语的性数格与连写也无法用替换处理。 因此统一改为「翻译前注入约束 + 翻译后只校验不替换」。
 *   <li><b>区间屏蔽 + 长词优先</b>：术语表同时含「纯棉」与「棉」时，命中「纯棉」后屏蔽其占用区间， 避免「棉」在同一区间重复命中。
 *   <li><b>单字术语默认跳过</b>：单字误匹配率极高（「包」会命中「面包」）。
 *   <li><b>按规模切换策略</b>：术语库 ≤ 200 条时 RAG 属过度设计，直接全量注入； 超过阈值才走「字面召回为主 + 向量近义召回为辅」。
 * </ul>
 */
public final class GlossaryRecall {

    /** 字面命中：整段文本与术语完全相等。 */
    public static final double CONF_LITERAL_EXACT = 1.0;

    /** 字面命中：术语作为子串出现在文本中。 */
    public static final double CONF_LITERAL_CONTAIN = 0.9;

    /** 向量召回的置信度基线，实际得分 = 基线 × 相似度。 */
    public static final double CONF_SEMANTIC_BASE = 0.75;

    /**
     * 向量召回最低相似度门槛。
     *
     * <p>实测（265 条术语库）显示 0.45~0.55 区间的召回基本是噪声，仅 &gt; 0.6 可信， 因此短术语的向量检索只能作为补充，主力仍是字面匹配 + 同义词归一。
     */
    public static final double SEMANTIC_MIN_SCORE = 0.60;

    /** 单字术语不参与字面匹配。 */
    public static final int MIN_LITERAL_TERM_LEN = 2;

    /** 术语库小于此规模时全量注入，不启用检索。 */
    public static final int SCALE_THRESHOLD_FULL_INJECT = 200;

    /** 超过此规模才值得引入向量召回（当前实现仅在超大库时启用）。 */
    public static final int SCALE_THRESHOLD_HYBRID = 2000;

    /** 注入 prompt 的条数上限，避免 prompt 膨胀。 */
    public static final int MAX_MANDATORY = 40;

    public static final int MAX_OPTIONAL = 12;

    /** 允许原样保留校验的术语类型：无形态变化，字符串比较安全。 */
    private static final Set<String> SAFE_TYPES = Set.of("brand", "model", "proper_noun", "unit");

    /**
     * 同义词归一表：把口语化表达映射到标准术语词，再做字面匹配。
     *
     * <p>这是术语召回的主力方案 —— 词典工程比向量工程更可控、可解释、可维护。
     */
    private static final Map<String, String> SYNONYMS = buildSynonyms();

    private static final List<String> SYNONYM_KEYS = sortByLengthDesc(SYNONYMS.keySet());

    private GlossaryRecall() {}

    /** 术语条目，{@code priority} 对应库里的 id，仅用于稳定排序。 */
    public record Term(
            long priority,
            String zh,
            String foreign,
            String termType,
            String source,
            double score,
            double rawScore) {

        public static Term of(long id, String zh, String foreign, String termType) {
            return new Term(
                    id,
                    zh == null ? "" : zh.trim(),
                    foreign == null ? "" : foreign.trim(),
                    termType == null || termType.isBlank()
                            ? "general"
                            : termType.trim().toLowerCase(Locale.ROOT),
                    "user",
                    0.0,
                    0.0);
        }

        Term scored(double value) {
            return new Term(priority, zh, foreign, termType, source, value, value);
        }

        /** 向量召回结果：置信度按基线折算，原始相似度单独保留便于调参。 */
        public static Term semantic(String zh, String foreign, String termType, double rawScore) {
            return new Term(
                    0,
                    zh == null ? "" : zh.trim(),
                    foreign == null ? "" : foreign.trim(),
                    termType == null || termType.isBlank() ? "general" : termType,
                    "rag_semantic",
                    CONF_SEMANTIC_BASE * rawScore,
                    rawScore);
        }

        /** 只对纯 ASCII 术语做原样保留校验：中文专有名词无法用字符串判断。 */
        boolean safeToCheckRetention() {
            return SAFE_TYPES.contains(termType) && !foreign.isBlank() && isAscii(zh);
        }

        public Map<String, Object> toDict() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("zh", zh);
            out.put("foreign", foreign);
            out.put("term_type", termType);
            out.put("source", source);
            out.put("score", round3(score));
            out.put("raw_score", round3(rawScore));
            return out;
        }
    }

    /** 召回结果：mandatory 为原文命中（强约束），optional 为语义相关（仅供参考）。 */
    public record Recalled(List<Term> mandatory, List<Term> optional) {

        public Recalled {
            mandatory = mandatory == null ? List.of() : List.copyOf(mandatory);
            optional = optional == null ? List.of() : List.copyOf(optional);
        }

        public boolean isEmpty() {
            return mandatory.isEmpty() && optional.isEmpty();
        }
    }

    /** 字面命中：起始位置 + 术语。 */
    private record Hit(int index, Term term) {}

    /** 同义词归一化：长键优先替换，避免短键覆盖长键。 */
    public static String normalizeWithSynonyms(String text) {
        if (text == null || text.isEmpty()) return "";
        String out = text;
        for (String key : SYNONYM_KEYS)
            if (out.contains(key)) out = out.replace(key, SYNONYMS.get(key));
        return out;
    }

    /**
     * 字面匹配：按术语长度降序扫描，命中后屏蔽占用区间，最后按位置升序返回。
     *
     * <p>先长后短 + 区间屏蔽，是为了让「纯棉」命中后「棉」不会在同一区间重复命中。
     */
    static List<Hit> matchLiteral(List<Term> terms, String text, int minTermLen) {
        List<Hit> hits = new ArrayList<>();
        if (text.isEmpty() || terms.isEmpty()) return hits;

        boolean[] occupied = new boolean[text.length()];
        List<Term> ordered = new ArrayList<>(terms);
        ordered.sort((a, b) -> Integer.compare(b.zh().length(), a.zh().length()));

        for (Term term : ordered) {
            if (term.zh().length() < minTermLen) continue;
            int start = 0;
            while (true) {
                int index = text.indexOf(term.zh(), start);
                if (index < 0) break;
                int end = index + term.zh().length();
                boolean overlaps = false;
                for (int k = index; k < end; k++)
                    if (occupied[k]) {
                        overlaps = true;
                        break;
                    }
                if (!overlaps) {
                    hits.add(new Hit(index, term));
                    for (int k = index; k < end; k++) occupied[k] = true;
                }
                start = index + 1;
            }
        }
        hits.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return hits;
    }

    /**
     * 分层召回。
     *
     * @param text 源文（中文）
     * @param terms 用户术语库（同一目标语言）
     * @param semantic 向量召回候选，已按相似度过滤；术语库规模未超阈值时传 null
     */
    public static Recalled retrieve(String text, List<Term> terms, List<Term> semantic) {
        if (terms == null || terms.isEmpty()) return new Recalled(List.of(), List.of());

        String normalized = normalizeWithSynonyms(text);

        if (terms.size() <= SCALE_THRESHOLD_FULL_INJECT) {
            // 小库：全量注入。字面命中只用于给置信度，没有命中时仍把全库交给模型自行判断。
            List<Term> mandatory = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Hit hit : matchLiteral(terms, normalized, MIN_LITERAL_TERM_LEN)) {
                if (!seen.add(hit.term().zh())) continue;
                mandatory.add(hit.term().scored(confidence(normalized, hit.term())));
            }
            if (mandatory.isEmpty()) mandatory = new ArrayList<>(terms);
            stableSortByScore(mandatory);
            return new Recalled(mandatory, List.of());
        }

        // 大库：字面精确召回 + 向量近义召回
        List<Term> mandatory = new ArrayList<>();
        Set<String> hit = new LinkedHashSet<>();
        for (Hit item : matchLiteral(terms, normalized, MIN_LITERAL_TERM_LEN)) {
            if (!hit.add(item.term().zh())) continue;
            mandatory.add(item.term().scored(confidence(normalized, item.term())));
        }
        stableSortByScore(mandatory);

        List<Term> optional = new ArrayList<>();
        if (semantic != null)
            for (Term term : semantic)
                if (!hit.contains(term.zh()) && term.rawScore() >= SEMANTIC_MIN_SCORE)
                    optional.add(term);
        optional.sort((a, b) -> Double.compare(b.rawScore(), a.rawScore()));
        return new Recalled(mandatory, optional);
    }

    public static String constraintBlock(Recalled recalled) {
        return constraintBlock(recalled, MAX_MANDATORY, MAX_OPTIONAL);
    }

    /**
     * 生成注入 LLM 的术语约束文本。
     *
     * <p>强制项与参考项分级表述，避免模型把参考项也当硬约束而产出生硬译文。
     */
    public static String constraintBlock(Recalled recalled, int maxMandatory, int maxOptional) {
        if (recalled == null || recalled.isEmpty()) return "";
        List<String> blocks = new ArrayList<>();
        List<Term> mandatory = head(recalled.mandatory(), maxMandatory);
        if (!mandatory.isEmpty())
            blocks.add(
                    section(
                            "【术语强制对照表】以下术语在译文中必须使用指定译法，" + "并请根据目标语言的语法规则正确处理单复数、冠词、性数格与连写：",
                            mandatory));
        List<Term> optional = head(recalled.optional(), maxOptional);
        if (!optional.isEmpty())
            blocks.add(section("【相关术语参考】以下术语与本文案语义相关，" + "如原文含义匹配请优先采用其译法，若不适用则忽略：", optional));
        return String.join("\n\n", blocks);
    }

    /**
     * 落地校验：只检测不替换。
     *
     * <p>匹配前去掉空格并转小写，兼容阿拉伯语连写与大小写差异。
     */
    public static Map<String, Object> verify(String translation, Recalled recalled) {
        List<Term> mandatory =
                recalled == null
                        ? List.of()
                        : recalled.mandatory().stream()
                                .filter(t -> !t.foreign().isBlank())
                                .toList();
        Map<String, Object> report = new LinkedHashMap<>();
        if (translation == null || translation.isBlank() || mandatory.isEmpty()) {
            report.put("checked", 0);
            report.put("violations", List.of());
            report.put("compliance_rate", 1.0);
            return report;
        }
        String normalized = squash(translation);
        List<String> violations = new ArrayList<>();
        for (Term term : mandatory)
            if (!normalized.contains(squash(term.foreign()))) violations.add(term.zh());
        report.put("checked", mandatory.size());
        report.put("violations", violations);
        report.put("compliance_rate", round3(1.0 - (double) violations.size() / mandatory.size()));
        return report;
    }

    /**
     * 专有名词保留检测（品牌 / 型号 / 单位）。
     *
     * <p>只在术语本身已是目标语言可识别形式（纯 ASCII）时才做字符串校验： 中文专有名词无法判断模型译成了什么，盲目替换只会产出中英混杂。
     *
     * @return 未被原样保留的专有名词
     */
    public static List<String> properNounMissing(String translation, Recalled recalled) {
        if (recalled == null) return List.of();
        String lowered = translation == null ? "" : translation.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        for (Term term : recalled.mandatory())
            if (term.safeToCheckRetention()
                    && !lowered.contains(term.zh().toLowerCase(Locale.ROOT)))
                missing.add(term.zh());
        return missing;
    }

    /** 翻译链路的完整报告：校验结果 + 注入清单 + 专有名词缺失。 */
    public static Map<String, Object> verifyDetailed(String translation, Recalled recalled) {
        Map<String, Object> report = verify(translation, recalled);
        report.put("proper_noun_missing", properNounMissing(translation, recalled));
        report.put(
                "terms_injected",
                recalled == null
                        ? List.of()
                        : recalled.mandatory().stream().map(Term::zh).toList());
        report.put("checked_on", "versionA");
        return report;
    }

    private static String section(String title, List<Term> terms) {
        StringBuilder sb = new StringBuilder(title).append('\n');
        for (Term term : terms)
            sb.append("- ").append(term.zh()).append(" → ").append(term.foreign()).append('\n');
        return sb.toString().stripTrailing();
    }

    private static List<Term> head(List<Term> terms, int max) {
        return terms.size() <= max ? terms : terms.subList(0, max);
    }

    private static double confidence(String normalized, Term term) {
        return normalized.strip().equals(term.zh()) ? CONF_LITERAL_EXACT : CONF_LITERAL_CONTAIN;
    }

    private static void stableSortByScore(List<Term> terms) {
        // 置信度降序；同分保持原文出现位置（List.sort 是稳定排序）
        terms.sort((a, b) -> Double.compare(b.score(), a.score()));
    }

    private static String squash(String value) {
        return value.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) > 127) return false;
        return true;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static Map<String, String> buildSynonyms() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("排汗", "速干");
        map.put("吸湿排汗", "速干");
        map.put("快干", "速干");
        map.put("易干", "速干");
        map.put("不闷", "透气");
        map.put("通风", "透气");
        map.put("透风", "透气");
        map.put("清爽", "透气");
        map.put("遮挡", "防晒");
        map.put("防紫外线", "防晒");
        map.put("遮阳", "防晒");
        map.put("显瘦", "修身");
        map.put("贴身", "修身");
        map.put("合身", "修身");
        map.put("收腰", "修身");
        map.put("纯棉质地", "纯棉");
        map.put("全棉", "纯棉");
        map.put("保温", "保暖");
        map.put("御寒", "保暖");
        map.put("防泼水", "防水");
        map.put("拒水", "防水");
        return Map.copyOf(map);
    }

    private static List<String> sortByLengthDesc(Set<String> keys) {
        List<String> list = new ArrayList<>(keys);
        list.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return List.copyOf(list);
    }
}
