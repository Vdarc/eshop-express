package com.traslation.backend.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Component
public class GatewayJwtFilter implements GlobalFilter, Ordered {
    private final javax.crypto.SecretKey key;

    public GatewayJwtFilter(@Value("${app.jwt.secret}") String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (GatewayAccessRules.isPublic(exchange.getRequest().getMethod().name(), path))
            return chain.filter(exchange);
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return unauthorized(exchange);
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(header.substring(7))
                            .getPayload();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("role_keys", List.class);
            if (GatewayAccessRules.requiresAdmin(path)
                    && (roles == null || !roles.contains("admin"))) return forbidden(exchange);

            // 迁移期兼容：Java 签发的 token 把用户 ID 放在 sub，Flask 签发的放在 user_id。
            String userId = GatewayAccessRules.resolveUserId(claims);
            if (userId == null) return unauthorized(exchange);
            String username = Objects.toString(claims.get("username"), "");
            if (username.isBlank() || "null".equals(username)) username = "anonymous";

            final String headerUserId = userId, headerUsername = username;
            ServerHttpRequest request =
                    exchange.getRequest()
                            .mutate()
                            .headers(
                                    h -> {
                                        h.remove("X-User-Id");
                                        h.remove("X-User-Name");
                                        h.remove("X-Role-Keys");
                                        h.set("X-User-Id", headerUserId);
                                        h.set("X-User-Name", headerUsername);
                                        h.set(
                                                "X-Role-Keys",
                                                roles == null ? "" : String.join(",", roles));
                                    })
                            .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (RuntimeException e) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes =
                "{\"code\":401,\"msg\":\"\u672a\u767b\u5f55\u6216\u767b\u5f55\u5df2\u8fc7\u671f\",\"data\":null}"
                        .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes =
                "{\"code\":403,\"msg\":\"\u6743\u9650\u4e0d\u8db3\",\"data\":null}"
                        .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
