package com.traslation.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlHours;
    private final MacAlgorithm algorithm;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours}") long ttlHours,
            @Value("${app.jwt.algorithm:HS256}") String algorithm) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlHours = ttlHours;
        this.algorithm = macAlgorithm(algorithm);
    }

    private static MacAlgorithm macAlgorithm(String name) {
        return switch (name == null ? "" : name.trim().toUpperCase()) {
            case "HS384" -> Jwts.SIG.HS384;
            case "HS512" -> Jwts.SIG.HS512;
            default -> Jwts.SIG.HS256;
        };
    }

    /**
     * 签发 token。
     *
     * <p>算法必须显式指定：JJWT 的 {@code signWith(key)} 会按密钥长度自动选择 （64 字节以上选 HS512），而 Flask 侧固定用
     * HS256（{@code JWT_ALGORITHM}）。 不显式指定会导致 Java 签发的 token 在 Flask 侧验签失败，迁移期间两边无法互认。
     */
    public String issue(long id, String username, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(id))
                .claim("username", username)
                .claim("role_keys", roles)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlHours * 3600)))
                .signWith(key, algorithm)
                .compact();
    }

    public io.jsonwebtoken.Claims parse(String token) {
        // 解析时不锁定算法：迁移期需要同时接受 Java(HS256) 与历史 Flask token
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
