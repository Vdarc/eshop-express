package com.traslation.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class RecommendationCacheService {
    private static final String PREFIX = "recommend:";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final int failureThreshold;
    private final long cooldownSeconds;
    private final String host;
    private final int port;
    private final int database;
    private int failures;
    private long downUntilMillis;
    private Boolean available;

    public RecommendationCacheService(
            StringRedisTemplate redis,
            ObjectMapper json,
            @Value("${app.redis.failure-threshold:3}") int failureThreshold,
            @Value("${app.redis.cooldown-seconds:30}") long cooldownSeconds,
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.database:1}") int database) {
        this.redis = redis;
        this.json = json;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownSeconds = Math.max(1, cooldownSeconds);
        this.host = host;
        this.port = port;
        this.database = database;
    }

    public Map<String, Object> get(String country, String category, String keyword) {
        if (inCooldown()) return null;
        String key = key(country, category, keyword);
        String value;
        try {
            value = redis.opsForValue().get(key);
            markSuccess();
        } catch (Exception e) {
            markFailure();
            return null;
        }
        if (value == null || value.isBlank()) return null;
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            try {
                redis.delete(key);
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    public void put(String country, String category, String keyword, Map<String, Object> result) {
        if (inCooldown()) return;
        try {
            redis.opsForValue()
                    .set(
                            key(country, category, keyword),
                            json.writeValueAsString(result),
                            Duration.ofSeconds(1800));
            markSuccess();
        } catch (Exception e) {
            markFailure();
        }
    }

    public long evict(String country, String category) {
        if (inCooldown()) return 0;
        String pattern =
                country != null && !country.isBlank() && category != null && !category.isBlank()
                        ? PREFIX + country + ":" + category + ":*"
                        : PREFIX + "*";
        try {
            Long deleted =
                    redis.execute(
                            (RedisCallback<Long>) connection -> deleteMatches(connection, pattern));
            markSuccess();
            return deleted == null ? 0 : deleted;
        } catch (Exception e) {
            markFailure();
            return 0;
        }
    }

    public boolean available() {
        if (inCooldown()) return false;
        try {
            Boolean pong =
                    redis.execute(
                            (RedisCallback<Boolean>)
                                    connection -> "PONG".equalsIgnoreCase(connection.ping()));
            markSuccess();
            return Boolean.TRUE.equals(pong);
        } catch (Exception e) {
            markFailure();
            return false;
        }
    }

    public synchronized Map<String, Object> state() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", true);
        out.put("available", available);
        out.put("circuit_open", inCooldown());
        out.put(
                "cooldown_remaining",
                Math.max(
                        0,
                        Math.round((downUntilMillis - System.currentTimeMillis()) / 100.0) / 10.0));
        out.put("consecutive_failures", failures);
        out.put("host", host + ":" + port);
        out.put("db", database);
        return out;
    }

    private Long deleteMatches(RedisConnection connection, String pattern) {
        long deleted = 0;
        Set<String> batch = new LinkedHashSet<>();
        try (Cursor<byte[]> cursor =
                connection.scan(ScanOptions.scanOptions().match(pattern).count(500).build())) {
            while (cursor.hasNext()) {
                batch.add(new String(cursor.next(), StandardCharsets.UTF_8));
                if (batch.size() == 500) {
                    deleted += deleteBatch(connection, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) deleted += deleteBatch(connection, batch);
        return deleted;
    }

    private long deleteBatch(RedisConnection connection, Set<String> keys) {
        byte[][] raw =
                keys.stream().map(redis.getStringSerializer()::serialize).toArray(byte[][]::new);
        Long count = connection.del(raw);
        return count == null ? 0 : count;
    }

    /** 键格式需与已上线数据保持兼容：recommend:{country}:{category}:{keyword 前 20 字} */
    String key(String country, String category, String keyword) {
        String safeKeyword =
                keyword == null ? "" : keyword.substring(0, Math.min(20, keyword.length()));
        return PREFIX
                + Objects.toString(country, "")
                + ":"
                + Objects.toString(category, "")
                + ":"
                + safeKeyword;
    }

    private synchronized boolean inCooldown() {
        return System.currentTimeMillis() < downUntilMillis;
    }

    private synchronized void markSuccess() {
        available = true;
        failures = 0;
        downUntilMillis = 0;
    }

    private synchronized void markFailure() {
        failures++;
        if (failures >= failureThreshold && !inCooldown()) {
            available = false;
            downUntilMillis = System.currentTimeMillis() + cooldownSeconds * 1000;
        }
    }
}
