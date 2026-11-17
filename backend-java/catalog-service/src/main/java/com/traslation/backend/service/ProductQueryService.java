package com.traslation.backend.service;

import com.traslation.backend.search.ProductSearchIndex;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

@Service
public class ProductQueryService {
    /** 与 MySQL 全文索引 ft_product_search 的列完全一致，顺序不可改。 */
    private static final String SEARCH_FIELDS =
            "zh_title,zh_desc,en_title,en_desc,ar_title,ar_desc,fr_title,fr_desc,"
                    + "de_title,de_desc,ja_title,ja_desc,ko_title,ko_desc,ru_title,ru_desc";

    private final JdbcTemplate db;
    private final ProductSearchIndex searchIndex;

    public ProductQueryService(JdbcTemplate db, ProductSearchIndex searchIndex) {
        this.db = db;
        this.searchIndex = searchIndex;
    }

    public Map<String, Object> list(
            int page, int size, String search, String category, String country, String language) {
        if ((search != null && !search.isBlank()) || searchIndex.useForBrowse()) {
            var esResult = searchIndex.searchHits(search, page, size, language, category, country);
            if (esResult != null) return formatEsResult(esResult, page, size, country);
        }
        String lang = ProductQuerySupport.normalizeLang(language);
        int p = Math.max(1, page), n = Math.min(Math.max(1, size), 100);
        String title = lang + "_title";
        StringBuilder where = new StringBuilder(" where 1=1 ");
        List<Object> args = new ArrayList<>();
        boolean hasSearch = search != null && !search.isBlank();
        if (hasSearch) {
            // 与 Flask 一致：走 ngram 全文索引，而不是前置通配符 LIKE（那样无法用索引且有召回差异）
            where.append(" and match(")
                    .append(SEARCH_FIELDS)
                    .append(") against(? in boolean mode)");
            args.add(search.trim());
        }
        if (category != null && !category.isBlank()) {
            where.append(" and p.category=?");
            args.add(category);
        }
        if (country != null && !country.isBlank()) {
            where.append(" and p.country=?");
            args.add(country);
        }
        Integer total =
                db.queryForObject(
                        "select count(*) from product p" + where, args.toArray(), Integer.class);
        List<Object> pageArgs = new ArrayList<>(args);
        String orderBy = " order by p.id desc";
        if (hasSearch) {
            orderBy =
                    " order by match("
                            + SEARCH_FIELDS
                            + ") against(? in boolean mode) desc, p.id desc";
            pageArgs.add(search.trim());
        }
        pageArgs.add((p - 1) * n);
        pageArgs.add(n);
        // 取全列：Flask 的响应包含 13 个语种的标题/描述，只取当前语言会导致前端缺字段。
        List<Map<String, Object>> rows =
                db.queryForList(
                        "select p.* from product p" + where + orderBy + " limit ?,?",
                        pageArgs.toArray());
        // product.id 现在是雪花（19 位），必须用 Long 承载，intValue() 会溢出
        List<Long> ids = rows.stream().map(x -> ((Number) x.get("id")).longValue()).toList();
        Map<Long, List<String>> images = new HashMap<>();
        if (!ids.isEmpty()) {
            String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
            // 一条 IN 查询覆盖整页图片，避免逐商品查询（N+1）
            images =
                    ProductQuerySupport.groupImages(
                            db.queryForList(
                                    "select product_id,image_url from product_image where"
                                            + " product_id in ("
                                            + marks
                                            + ") order by sort,id",
                                    ids.toArray()));
        }
        Map<String, String> publishTargets = publishTargetLanguages(rows);
        List<Map<String, Object>> products = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            // 必须用 long：① 雪花 ID 有 19 位，intValue() 会截断；
            // ② images 的键类型是 Long，用 int 查会装箱成 Integer 而永远匹配不到 Long 键
            //    （Map.get 用 equals 比较，Integer(1) != Long(1)），导致整页 image_urls 恒为空。
            long id = ((Number) row.get("id")).longValue();
            String esin = String.valueOf(row.get("esin"));
            products.add(
                    ProductQuerySupport.formatProduct(
                            row,
                            images.getOrDefault(id, List.of()),
                            publishTargets.get(esin),
                            country,
                            300,
                            true));
        }
        int count = total == null ? 0 : total;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", p);
        result.put("pages", ProductQuerySupport.totalPages(count, n));
        result.put("products", products);
        result.put("total", count);
        return result;
    }

    /** ES 命中统一走 formatProduct，保证与 MySQL 路径字段一致，并附 score/highlight。 */
    private Map<String, Object> formatEsResult(
            com.traslation.backend.search.ProductSearchIndex.EsResult result,
            int page,
            int size,
            String country) {
        int n = Math.min(100, Math.max(1, size));
        List<Map<String, Object>> products = new ArrayList<>();
        for (var hit : result.hits()) {
            Map<String, Object> source = hit.source();
            List<String> urls = new ArrayList<>();
            Object image = source.get("image_url");
            if (image != null && !String.valueOf(image).isBlank()) urls.add(String.valueOf(image));
            Map<String, Object> product =
                    ProductQuerySupport.formatProduct(source, urls, null, country, 300, false);
            product.put("score", hit.score());
            if (hit.highlight() != null) product.put("highlight", hit.highlight());
            products.add(product);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current", Math.max(1, page));
        out.put("pages", ProductQuerySupport.totalPages(result.total(), n));
        out.put("products", products);
        out.put("total", result.total());
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castUrls(Object value) {
        return value instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    /** 批量取发布记录里的首个目标语言，避免逐个商品查询。 */
    private Map<String, String> publishTargetLanguages(List<Map<String, Object>> rows) {
        Map<String, String> targets = new HashMap<>();
        List<String> esins =
                rows.stream()
                        .map(r -> Objects.toString(r.get("esin"), ""))
                        .filter(a -> !a.isBlank())
                        .toList();
        if (esins.isEmpty()) return targets;
        String marks = String.join(",", Collections.nCopies(esins.size(), "?"));
        db.queryForList(
                        "select product_id,target_languages from publish_record where product_id in"
                                + " ("
                                + marks
                                + ")",
                        esins.toArray())
                .forEach(
                        row -> {
                            String first =
                                    firstLanguage(
                                            Objects.toString(row.get("target_languages"), ""));
                            if (first != null)
                                targets.put(Objects.toString(row.get("product_id"), ""), first);
                        });
        return targets;
    }

    /** 解析 '["en","ar"]' 取第一个语言；解析失败返回 null（不阻断商品返回）。 */
    private static String firstLanguage(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.replace("[", "").replace("]", "").replace("\"", "").trim();
        if (cleaned.isEmpty()) return null;
        for (String part : cleaned.split(",")) {
            String value = part.trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return null;
    }

    public Map<String, Object> byEsin(String esin, String language, String country) {
        List<Map<String, Object>> rows =
                db.queryForList("select p.* from product p where p.esin=?", esin);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        long id = ((Number) row.get("id")).longValue();
        Map<Long, List<String>> images =
                ProductQuerySupport.groupImages(
                        db.queryForList(
                                "select product_id,image_url from product_image where product_id=?"
                                        + " order by sort,id",
                                id));
        Map<String, String> targets = publishTargetLanguages(List.of(row));
        Map<String, Object> product =
                ProductQuerySupport.formatProduct(
                        row,
                        images.getOrDefault(id, List.of()),
                        targets.get(esin),
                        country,
                        600,
                        true);
        // 详情额外返回缩略图：Flask 取前 4 张，无图时用占位图生成 4 张
        List<String> urls = castUrls(product.get("image_urls"));
        List<String> thumbs = new ArrayList<>();
        if (urls.isEmpty()) {
            int seed = Math.abs(String.valueOf(esin).hashCode()) % 1000;
            for (int i = 0; i < 4; i++)
                thumbs.add("https://picsum.photos/seed/" + (seed + i) + "/100/100");
        } else {
            thumbs.addAll(urls.subList(0, Math.min(4, urls.size())));
        }
        product.put("thumbnail_urls", thumbs);
        return product;
    }

    public Map<String, Object> merchantProducts(long userId, String search) {
        String q = search == null ? "" : search.trim();
        String filter =
                q.isBlank() ? "" : " and (esin like ? or zh_title like ? or en_title like ?)";
        Object[] args =
                q.isBlank()
                        ? new Object[] {userId}
                        : new Object[] {userId, "%" + q + "%", "%" + q + "%", "%" + q + "%"};
        var rows =
                db.queryForList(
                        "select"
                            + " id,esin,category,zh_title,zh_desc,en_title,en_desc,country,price,create_time"
                            + " from product where user_id=?"
                                + filter
                                + " order by create_time desc limit 50",
                        args);
        for (var p : rows) {
            // 同 list() 的坑：product.id 是 19 位雪花，intValue() 会截断成本不该匹配的值，
            // 拿去查 bigint 列 product_image.product_id 必然查不到 → 商家自己看不到已上传的图。
            long id = ((Number) p.get("id")).longValue();
            var images =
                    db.queryForList(
                            "select image_url from product_image where product_id=? order by"
                                    + " sort,id",
                            String.class,
                            id);
            p.put("image_urls", images);
            p.put("image_url", images.isEmpty() ? null : images.get(0));
        }
        return Map.of(
                "products",
                rows,
                "total",
                rows.size(),
                "current",
                1,
                "per_page",
                50,
                "pages",
                rows.isEmpty() ? 0 : 1);
    }

    public boolean owns(long userId, String esin) {
        Integer count =
                db.queryForObject(
                        "select count(*) from product where user_id=? and esin=?",
                        Integer.class,
                        userId,
                        esin);
        return count != null && count > 0;
    }

    public int updateMerchantProduct(long userId, String esin, Map<String, Object> body) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (String field : List.of("category", "zh_title", "zh_desc", "country"))
            if (body.containsKey(field)) {
                sets.add(field + "=?");
                args.add(body.get(field));
            }
        String lang = Objects.toString(body.get("target_language"), "");
        if (ProductQuerySupport.LANGS.contains(lang) && body.containsKey("foreign_title")) {
            sets.add(lang + "_title=?");
            args.add(body.get("foreign_title"));
        }
        if (ProductQuerySupport.LANGS.contains(lang) && body.containsKey("foreign_desc")) {
            sets.add(lang + "_desc=?");
            args.add(body.get("foreign_desc"));
        }
        if (sets.isEmpty()) return 0;
        args.add(userId);
        args.add(esin);
        int count =
                db.update(
                        "update product set "
                                + String.join(",", sets)
                                + " where user_id=? and esin=?",
                        args.toArray());
        if (count > 0) refreshIndexAfterCommit(esin);
        return count;
    }

    /**
     * 索引刷新放到事务提交之后。
     *
     * <p>原实现在 update 语句之后立即调用，若随后事务回滚，ES 已被改写而 MySQL 未变，两边从此不一致（
     * 且只能等 Canal 补偿）。与 {@code ProductPublishService} 的 afterCommit 保持一致。
     *
     * <p>该方法也可能在没有事务的上下文被调用（控制器未标 {@code @Transactional} 时会这样），
     * 此时 {@code registerSynchronization} 会抛 IllegalStateException，故先判断同步是否激活。
     */
    private void refreshIndexAfterCommit(String esin) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                searchIndex.indexProduct(esin);
                            } catch (Exception ignored) {
                                // 索引失败不应回滚已提交的数据库写入；Canal / reconcile 会补偿
                            }
                        }
                    });
            return;
        }
        try {
            searchIndex.indexProduct(esin);
        } catch (Exception ignored) {
            // 同上
        }
    }

    public void unpublishMerchantProduct(long userId, String esin) {
        db.update("delete from publish_record where product_id=? and user_id=?", esin, userId);
        // 原实现从 byEsin() 的返回体取 id，但 formatProduct 从不输出 "id" 键 →
        // 恒取到 null → "delete ... where product_id=null" 删不掉任何行，留下孤儿图片。
        // 改为按 esin 反查主键，避免依赖响应体字段。
        db.update(
                "delete from product_image where product_id in"
                        + " (select id from product where user_id=? and esin=?)",
                userId,
                esin);
        db.update("delete from product where user_id=? and esin=?", userId, esin);
        searchIndex.deleteProduct(esin);
    }

    public List<String> suggest(String keyword, int size) {
        int limit = Math.min(20, Math.max(1, size));
        String q = "%" + keyword.trim() + "%";
        return db.queryForList(
                "select distinct coalesce(nullif(en_title,''),nullif(zh_title,''),esin) title from"
                    + " product where en_title like ? or zh_title like ? or esin like ? order by"
                    + " title limit "
                        + limit,
                String.class,
                q,
                q,
                q);
    }
}
