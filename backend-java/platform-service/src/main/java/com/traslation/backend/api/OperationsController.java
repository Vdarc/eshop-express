package com.traslation.backend.api;

import com.traslation.backend.common.BusinessException;
import com.traslation.backend.common.id.SnowflakeIdGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OperationsController {
    private final JdbcTemplate db;
    private final SnowflakeIdGenerator idGenerator;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public OperationsController(JdbcTemplate db, SnowflakeIdGenerator idGenerator) {
        this.db = db;
        this.idGenerator = idGenerator;
    }

    @PostMapping("/stat/click")
    public Map<String, Object> click(@RequestBody Map<String, Object> b) {
        String id = Objects.toString(b.get("product_id"), "").trim();
        if (id.isEmpty()) return Map.of("code", 400, "msg", "商品ID不能为空");
        db.update(
                "insert into biz_rec_stat_log(id,product_id,action_type,create_time)"
                    + " values(?,?,2,now())",
                idGenerator.nextId(),
                id);
        return Map.of("code", 200, "msg", "点击统计成功");
    }

    @GetMapping("/stat/daily")
    public Map<String, Object> daily(@RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        var counts =
                db.queryForMap(
                        "select sum(action_type=1) expose_count,sum(action_type=2) click_count from"
                                + " biz_rec_stat_log where create_time>=? and create_time<?",
                        Timestamp.valueOf(d.atStartOfDay()),
                        Timestamp.valueOf(d.plusDays(1).atStartOfDay()));
        long expose = num(counts.get("expose_count")), click = num(counts.get("click_count"));
        var top =
                db.queryForList(
                        "select product_id,count(*) click_count from biz_rec_stat_log where"
                            + " action_type=2 and create_time>=? and create_time<? group by"
                            + " product_id order by click_count desc limit 10",
                        Timestamp.valueOf(d.atStartOfDay()),
                        Timestamp.valueOf(d.plusDays(1).atStartOfDay()));
        return Map.of(
                "code",
                200,
                "data",
                Map.of(
                        "date",
                        d.toString(),
                        "total_expose",
                        expose,
                        "total_click",
                        click,
                        "click_rate",
                        (expose == 0 ? 0 : Math.round(click * 10000.0 / expose) / 100.0) + "%",
                        "top_products",
                        top));
    }

    @GetMapping("/stat/click-rank")
    public Map<String, Object> rank() {
        return Map.of(
                "code",
                200,
                "data",
                Map.of(
                        "top_products",
                        db.queryForList(
                                "select product_id,count(*) click_count from biz_rec_stat_log where"
                                    + " action_type=2 group by product_id order by click_count desc"
                                    + " limit 10")));
    }

    @PostMapping("/check-compliance")
    public Map<String, Object> compliance(@RequestBody Map<String, Object> b) {
        String text = Objects.toString(b.get("text"), "");
        Object platforms = b.get("platforms");
        List<?> names =
                platforms instanceof List<?> l ? l : List.of("amazon", "ebay", "aliexpress");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object obj : names) {
            String name = Objects.toString(obj).toLowerCase(Locale.ROOT);
            List<String> banned =
                    switch (name) {
                        case "amazon" -> List.of("free", "guarantee", "best", "amazing");
                        case "ebay" -> List.of("free shipping", "click here");
                        case "aliexpress" -> List.of("wholesale", "dropshipping");
                        default -> List.of();
                    };
            List<String> found =
                    banned.stream().filter(w -> text.toLowerCase(Locale.ROOT).contains(w)).toList();
            int limit = banned.isEmpty() ? Integer.MAX_VALUE : 512;
            List<String> messages = new ArrayList<>();
            if (text.length() > limit) messages.add("超出字符限制 (" + text.length() + "/" + limit + ")");
            if (!found.isEmpty()) messages.add("包含禁用词: " + String.join(", ", found));
            result.put(
                    name,
                    Map.of(
                            "passed",
                            text.length() <= limit && found.isEmpty(),
                            "current",
                            text.length(),
                            "limit",
                            limit,
                            "messages",
                            messages));
        }
        return Map.of("code", 200, "data", result);
    }

    @GetMapping("/user/profile")
    public Map<String, Object> profile(@RequestHeader("X-User-Id") long uid) {
        var rows =
                db.queryForList(
                        "select username,email,avatar_url,country,gender,occupation from sys_users"
                            + " where user_id=?",
                        uid);
        if (rows.isEmpty()) return Map.of("code", 404, "msg", "用户不存在");
        return Map.of("code", 200, "data", rows.get(0));
    }

    @PostMapping("/user/profile")
    @Transactional
    public Map<String, Object> updateProfile(
            @RequestHeader("X-User-Id") long uid, @RequestBody Map<String, Object> b) {
        // 唯一的用户标识列必须查重：sys_users 上 username / email 都有唯一约束，
        // 但本接口原先直接 update，与 /api/user/add、/api/user/update 的校验不一致——
        // 结果是"后台建号会挡住重名，用户自己改资料却能改重名"，
        // 而 login 用 `where username=? or email=? limit 1`，重名后取到哪一行不确定。
        assertUnique("username", b, uid);
        assertUnique("email", b, uid);
        updateField(b, "username", "username", uid);
        updateField(b, "email", "email", uid);
        updateField(b, "avatar_url", "avatar_url", uid);
        updateField(b, "country", "country", uid);
        updateField(b, "gender", "gender", uid);
        updateField(b, "occupation", "occupation", uid);
        String password = Objects.toString(b.get("password"), "");
        if (!password.isBlank())
            db.update(
                    "update sys_users set password=? where user_id=?",
                    passwords.encode(password),
                    uid);
        return Map.of("code", 200, "msg", "更新成功");
    }

    private void updateField(Map<String, Object> b, String key, String column, long uid) {
        if (b.containsKey(key))
            db.update("update sys_users set " + column + "=? where user_id=?", b.get(key), uid);
    }

    /**
     * 查重：仅当请求体带该字段且非空时检查，排除自己。
     *
     * <p>{@code column} 只接受调用方传入的字面量（username / email），不来自请求体，故不存在注入面。
     */
    private void assertUnique(String column, Map<String, Object> b, long uid) {
        if (!b.containsKey(column)) return;
        String value = Objects.toString(b.get(column), "").trim();
        if (value.isEmpty()) return;
        Integer taken =
                db.queryForObject(
                        "select count(*) from sys_users where " + column + "=? and user_id<>?",
                        Integer.class,
                        value,
                        uid);
        if (taken != null && taken > 0)
            throw new BusinessException(HttpStatus.CONFLICT, "该" + ("username".equals(column) ? "用户名" : "邮箱") + "已被占用");
    }

    @GetMapping("/merchant/info")
    public Map<String, Object> merchantInfo(@RequestHeader("X-User-Id") long uid) {
        var rows =
                db.queryForList(
                        "select shop_name,main_category,contact_name,contact_phone,contact_email"
                            + " from merchant_info where user_id=? limit 1",
                        uid);
        if (rows.isEmpty())
            return Map.of(
                    "code",
                    200,
                    "data",
                    Map.of(
                            "shop_name",
                            "",
                            "main_category",
                            "clothes",
                            "contact_name",
                            "",
                            "contact_phone",
                            "",
                            "contact_email",
                            ""));
        return Map.of("code", 200, "data", rows.get(0));
    }

    @PostMapping("/merchant/info")
    @Transactional
    public Map<String, Object> updateMerchantInfo(
            @RequestHeader("X-User-Id") long uid, @RequestBody Map<String, Object> b) {
        Integer count =
                db.queryForObject(
                        "select count(*) from merchant_info where user_id=?", Integer.class, uid);
        if (count == null || count == 0)
            db.update(
                    "insert into"
                        + " merchant_info(id,user_id,shop_name,main_category,contact_name,contact_phone,contact_email,status,create_time,update_time)"
                        + " values(?,?,?,?,?,?,?,'approved',now(),now())",
                    idGenerator.nextId(),
                    uid,
                    b.getOrDefault("shop_name", ""),
                    b.getOrDefault("main_category", "clothes"),
                    b.getOrDefault("contact_name", ""),
                    b.getOrDefault("contact_phone", ""),
                    b.getOrDefault("contact_email", ""));
        else
            db.update(
                    "update merchant_info set"
                        + " shop_name=?,main_category=?,contact_name=?,contact_phone=?,contact_email=?,update_time=now()"
                        + " where user_id=?",
                    b.getOrDefault("shop_name", ""),
                    b.getOrDefault("main_category", "clothes"),
                    b.getOrDefault("contact_name", ""),
                    b.getOrDefault("contact_phone", ""),
                    b.getOrDefault("contact_email", ""),
                    uid);
        return Map.of("code", 200, "msg", "更新成功");
    }

    private long num(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }
}
