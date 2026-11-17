package com.traslation.backend.api;

import com.traslation.backend.common.BusinessException;
import com.traslation.backend.common.id.SnowflakeIdGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class AdminController {
    private final JdbcTemplate db;
    private final SnowflakeIdGenerator idGenerator;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AdminController(JdbcTemplate db, SnowflakeIdGenerator idGenerator) {
        this.db = db;
        this.idGenerator = idGenerator;
    }

    @GetMapping("/user/list")
    public Map<String, Object> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int p = Math.max(1, page), n = Math.min(Math.max(1, size), 100);
        Integer total = db.queryForObject("select count(*) from sys_users", Integer.class);
        List<Map<String, Object>> list =
                db.queryForList(
                        "select user_id,username,email,status,"
                                + "date_format(create_time,'%Y-%m-%d %H:%i:%s') create_time"
                                + " from sys_users order by user_id limit ? offset ?",
                        n, (p - 1) * n);
        for (var user : list) {
            long id = ((Number) user.get("user_id")).longValue();
            user.put(
                    "role_ids",
                    db.queryForList(
                            "select r.role_id from sys_roles r join sys_user_roles ur on"
                                    + " ur.role_id=r.role_id where ur.user_id=?",
                            Long.class,
                            id));
            user.put(
                    "roles",
                    db.queryForList(
                            "select r.role_name from sys_roles r join sys_user_roles ur on"
                                    + " ur.role_id=r.role_id where ur.user_id=?",
                            String.class,
                            id));
        }
        int count = total == null ? 0 : total;
        return Map.of(
                "code",
                200,
                "data",
                Map.of(
                        "list",
                        list,
                        "total",
                        count,
                        "page",
                        p,
                        "size",
                        n,
                        "totalPages",
                        (count + n - 1) / n));
    }

    @GetMapping("/role/list")
    public Map<String, Object> roles() {
        return Map.of(
                "code",
                200,
                "data",
                db.queryForList("select role_id,role_name,role_key from sys_roles"));
    }

    @PostMapping("/user/add")
    @Transactional
    public Map<String, Object> addUser(@RequestBody Map<String, Object> b) {
        String username = required(b, "username"),
                email = required(b, "email"),
                password = required(b, "password");
        unique(username, email, null);
        // sys_users.user_id 已改为雪花主键且无 AUTO_INCREMENT，必须显式提供。
        // 原实现依赖自增 + 事后 select 回读，在现库上必然抛
        // ERROR 1364 "Field 'user_id' doesn't have a default value"。
        long id = idGenerator.nextId();
        db.update(
                "insert into sys_users(user_id,username,email,password,status) values(?,?,?,?,?)",
                id,
                username,
                email,
                passwords.encode(password),
                integer(b, "status", 1));
        replaceRoles(id, b.get("role_ids"));
        return ok();
    }

    @PostMapping("/user/update")
    @Transactional
    public Map<String, Object> updateUser(@RequestBody Map<String, Object> b) {
        long id = number(b, "user_id");
        String username = required(b, "username"), email = required(b, "email");
        if (db.queryForObject("select count(*) from sys_users where user_id=?", Integer.class, id)
                == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        unique(username, email, id);
        String password = Objects.toString(b.get("password"), "");
        if (password.isBlank())
            db.update(
                    "update sys_users set username=?,email=?,status=? where user_id=?",
                    username,
                    email,
                    integer(b, "status", 1),
                    id);
        else
            db.update(
                    "update sys_users set username=?,email=?,password=?,status=? where user_id=?",
                    username,
                    email,
                    passwords.encode(password),
                    integer(b, "status", 1),
                    id);
        replaceRoles(id, b.get("role_ids"));
        return ok();
    }

    @PostMapping("/user/delete")
    @Transactional
    public Map<String, Object> deleteUser(@RequestBody Map<String, Object> b) {
        long id = number(b, "user_id");
        db.update("delete from sys_user_roles where user_id=?", id);
        if (db.update("delete from sys_users where user_id=?", id) == 0)
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        return ok();
    }

    @GetMapping("/merchant/applications")
    public Map<String, Object> applications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int p = Math.max(1, page), n = Math.min(Math.max(1, size), 100);
        Integer total =
                db.queryForObject(
                        "select count(*) from merchant_info where status='pending'", Integer.class);
        var list =
                db.queryForList(
                        "select m.id,m.user_id,u.username,u.email,m.shop_name,m.main_category,"
                            + "m.contact_name,m.contact_phone,m.contact_email,m.status,date_format(m.create_time,'%Y-%m-%d"
                            + " %H:%i:%s') create_time from merchant_info m join sys_users u on"
                            + " u.user_id=m.user_id where m.status='pending' order by m.create_time"
                            + " desc limit ? offset ?",
                        n, (p - 1) * n);
        int count = total == null ? 0 : total;
        return Map.of(
                "code",
                200,
                "data",
                Map.of(
                        "list",
                        list,
                        "total",
                        count,
                        "page",
                        p,
                        "size",
                        n,
                        "totalPages",
                        (count + n - 1) / n));
    }

    @GetMapping("/merchant/detail")
    public Map<String, Object> merchantDetail(@RequestParam("id") long id) {
        var rows =
                db.queryForList(
                        "select m.id,m.user_id,u.username,u.email,m.shop_name,m.main_category,"
                            + "m.contact_name,m.contact_phone,m.contact_email,m.status,m.create_time,m.update_time"
                            + " from merchant_info m join sys_users u on u.user_id=m.user_id where"
                            + " m.id=?",
                        id);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "商家申请不存在");
        return Map.of("code", 200, "data", rows.get(0));
    }

    @PostMapping("/merchant/approve")
    @Transactional
    public Map<String, Object> approve(@RequestBody Map<String, Object> b) {
        return resolveMerchant(number(b, "application_id"), true);
    }

    @PostMapping("/merchant/reject")
    @Transactional
    public Map<String, Object> reject(@RequestBody Map<String, Object> b) {
        return resolveMerchant(number(b, "application_id"), false);
    }

    @GetMapping("/log/list")
    public Map<String, Object> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int p = Math.max(1, page), n = Math.min(Math.max(1, size), 200);
        Integer total = db.queryForObject("select count(*) from sys_oper_log", Integer.class);
        var list =
                db.queryForList(
                        "select id,username,action,method,cost_time,status,"
                                + "date_format(create_time,'%Y-%m-%d %H:%i:%s') create_time from"
                                + " sys_oper_log order by create_time desc limit ? offset ?",
                        n, (p - 1) * n);
        int count = total == null ? 0 : total;
        return Map.of(
                "code",
                200,
                "data",
                Map.of(
                        "list",
                        list,
                        "total",
                        count,
                        "page",
                        p,
                        "size",
                        n,
                        "totalPages",
                        (count + n - 1) / n));
    }

    private Map<String, Object> resolveMerchant(long id, boolean approve) {
        var rows = db.queryForList("select user_id,status from merchant_info where id=?", id);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "商家申请不存在");
        var row = rows.get(0);
        if (!"pending".equals(row.get("status")))
            throw new BusinessException(HttpStatus.CONFLICT, "该申请已处理");
        long userId = ((Number) row.get("user_id")).longValue();
        db.update(
                "update merchant_info set status=? where id=?",
                approve ? "approved" : "rejected",
                id);
        if (approve) {
            db.update("delete from sys_user_roles where user_id=?", userId);
            db.update(
                    "insert into sys_user_roles(user_id,role_id) select ?,role_id from sys_roles"
                            + " where role_key='merchant'",
                    userId);
        }
        return ok();
    }

    private void replaceRoles(long id, Object value) {
        db.update("delete from sys_user_roles where user_id=?", id);
        if (value instanceof Collection<?> roles)
            for (Object role : roles) {
                long rid = Long.parseLong(Objects.toString(role));
                db.update("insert into sys_user_roles(user_id,role_id) values(?,?)", id, rid);
            }
    }

    private void unique(String username, String email, Long except) {
        String sql =
                "select count(*) from sys_users where (username=? or email=?)"
                        + (except == null ? "" : " and user_id<>?");
        Object[] args =
                except == null
                        ? new Object[] {username, email}
                        : new Object[] {username, email, except};
        if (db.queryForObject(sql, Integer.class, args) > 0)
            throw new BusinessException(HttpStatus.CONFLICT, "用户名或邮箱已被使用");
    }

    private String required(Map<String, Object> b, String k) {
        String v = Objects.toString(b.get(k), "").trim();
        if (v.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, k + "不能为空");
        return v;
    }

    private long number(Map<String, Object> b, String k) {
        try {
            return Long.parseLong(Objects.toString(b.get(k)));
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, k + "必须是数字");
        }
    }

    private int integer(Map<String, Object> b, String k, int d) {
        try {
            return Integer.parseInt(Objects.toString(b.get(k), "" + d));
        } catch (Exception e) {
            return d;
        }
    }

    private Map<String, Object> ok() {
        return Map.of("code", 200, "msg", "成功");
    }
}
