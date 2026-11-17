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
     * <p>算法必须显式指定：JJWT 的 {@code signWith(key)} 会按密钥长度自动选择 （64 字节以上选 HS512），而本服务固定用
     * HS256（{@code JWT_ALGORITHM}）。 不显式指定会让签发与验签算法不一致，导致 token 无法互认。
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
        // 解析时不锁定算法：需同时接受标准 HS256 与带自定义 claim 的历史 token
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
