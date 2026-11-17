package com.traslation.backend.service;

import com.traslation.backend.common.BusinessException;
import com.traslation.backend.common.id.SnowflakeIdGenerator;
import com.traslation.backend.security.JwtService;
import com.traslation.backend.security.PasswordVerifier;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AuthService {
    private final JdbcTemplate db;
    private final JwtService jwt;
    private final SnowflakeIdGenerator idGenerator;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PasswordVerifier verifier = new PasswordVerifier();

    public AuthService(JdbcTemplate db, JwtService jwt, SnowflakeIdGenerator idGenerator) {
        this.db = db;
        this.jwt = jwt;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public Map<String, Object> register(Map<String, Object> body) {
        String username = required(body, "username"),
                email = required(body, "email"),
                password = required(body, "password");
        if (db.queryForObject(
                        "select count(*) from sys_users where username=? or email=?",
                        Integer.class,
                        username,
                        email)
                > 0) throw new BusinessException(HttpStatus.CONFLICT, "用户名或邮箱已注册");
        // user_id 由雪花算法生成：显式插入后无需再 select 回查主键（少一次往返）
        long id = idGenerator.nextId();
        db.update(
                "insert into sys_users(user_id,username,email,password,status) values(?,?,?,?,1)",
                id,
                username,
                email,
                encoder.encode(password));
        db.update(
                "insert into sys_user_roles(user_id,role_id) select ?,role_id from sys_roles where"
                        + " role_key='customer'",
                id);
        return Map.of("success", true, "message", "注册成功");
    }

    @Transactional
    public Map<String, Object> registerMerchant(Map<String, Object> body) {
        String username = required(body, "username"),
                email = required(body, "email"),
                password = required(body, "password");
        String shop = required(body, "shop_name"),
                category = required(body, "main_category"),
                contact = required(body, "contact_name"),
                phone = required(body, "contact_phone"),
                contactEmail = required(body, "contact_email");
        if (db.queryForObject(
                        "select count(*) from sys_users where username=? or email=?",
                        Integer.class,
                        username,
                        email)
                > 0) throw new BusinessException(HttpStatus.CONFLICT, "用户名或邮箱已注册");
        long id = idGenerator.nextId();
        db.update(
                "insert into sys_users(user_id,username,email,password,status) values(?,?,?,?,1)",
                id,
                username,
                email,
                encoder.encode(password));
        db.update(
                "insert into sys_user_roles(user_id,role_id) select ?,role_id from sys_roles where"
                        + " role_key='customer'",
                id);
        db.update(
                "insert into"
                    + " merchant_info(id,user_id,shop_name,main_category,contact_name,contact_phone,contact_email,status)"
                    + " values(?,?,?,?,?,?,?,'pending')",
                idGenerator.nextId(),
                id,
                shop,
                category,
                contact,
                phone,
                contactEmail);
        return Map.of("success", true, "message", "商家注册成功，等待管理员审核");
    }

    public Map<String, Object> login(Map<String, Object> body) {
        String login = required(body, "username"), password = required(body, "password");
        List<Map<String, Object>> rows =
                db.queryForList(
                        "select user_id,username,email,password,status from sys_users where"
                                + " username=? or email=? limit 1",
                        login,
                        login);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        var u = rows.get(0);
        String hash = Objects.toString(u.get("password"), "");
        if (((Number) u.get("status")).intValue() == 0)
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前账号已被禁用");
        // 兼容 BCrypt / werkzeug scrypt / werkzeug pbkdf2 三种历史格式
        boolean valid = verifier.matches(password, hash);
        if (!valid) throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        long id = ((Number) u.get("user_id")).longValue();
        List<String> roles =
                db.queryForList(
                        "select r.role_key from sys_roles r join sys_user_roles ur on"
                                + " ur.role_id=r.role_id where ur.user_id=?",
                        String.class,
                        id);
        List<String> perms =
                db.queryForList(
                        "select distinct m.perms from sys_menus m join sys_role_menus rm on"
                            + " rm.menu_id=m.menu_id join sys_user_roles ur on"
                            + " ur.role_id=rm.role_id where ur.user_id=? and m.status=1 and m.perms"
                            + " is not null",
                        String.class,
                        id);
        List<Map<String, Object>> menus =
                db.queryForList(
                        "select distinct m.menu_name as menuName,m.path from sys_menus m join"
                            + " sys_role_menus rm on rm.menu_id=m.menu_id join sys_user_roles ur on"
                            + " ur.role_id=rm.role_id where ur.user_id=? and m.status=1",
                        id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "登录成功");
        out.put("username", u.get("username"));
        out.put("user_id", id);
        out.put("roleKeys", roles);
        out.put("permissions", perms);
        out.put("menus", menus);
        out.put("token", jwt.issue(id, Objects.toString(u.get("username")), roles, perms));
        return out;
    }

    @Transactional
    public Map<String, Object> updatePassword(
            String authenticatedUserId, Map<String, Object> body) {
        String password = Objects.toString(body.get("new_password"), "");
        if (authenticatedUserId == null || authenticatedUserId.isBlank() || password.isBlank())
            throw new BusinessException(HttpStatus.BAD_REQUEST, "参数不能为空");
        long userId;
        try {
            userId = Long.parseLong(authenticatedUserId);
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        if (db.update(
                        "update sys_users set password=? where user_id=?",
                        encoder.encode(password),
                        userId)
                == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        return Map.of("success", true, "message", "密码修改成功");
    }

    private String required(Map<String, Object> b, String k) {
        String v = Objects.toString(b.get(k), "").trim();
        if (v.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, k + "不能为空");
        return v;
    }
}
