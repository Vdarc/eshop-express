package com.traslation.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务级缓存级联失效，对应 Flask 的 {@code CacheManager.delete_related_caches}。
 *
 * <p>键模板必须与 Python 侧完全一致，否则失效会「静默无效」—— 表现为数据改了但页面仍是旧值， 且日志里没有任何异常。这是跨语言共享缓存最典型的坑。
 *
 * <ul>
 *   <li>{@code product:info:{esin}}
 *   <li>{@code product:list:{user_id}:{category}}
 *   <li>{@code glossary:user:{user_id}:lang:{language}} / {@code glossary:user:{user_id}:all}
 *   <li>{@code recommend:{country}:{category}:{keyword}}
 * </ul>
 *
 * <p>删除用 SCAN 游标而不是 KEYS：KEYS 在大 key 空间下会阻塞 Redis 单线程。 任何异常只记日志不外抛 —— 缓存失效失败不应让业务写入失败。
 */
@Service
public class BusinessCacheEvictor {
    private static final Logger log = LoggerFactory.getLogger(BusinessCacheEvictor.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public BusinessCacheEvictor(
            StringRedisTemplate redis,
            @org.springframework.beans.factory.annotation.Value("${app.cache.evict-enabled:true}")
                    boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    /** 商品变更：详情缓存 + 该用户的列表缓存 + 全部推荐缓存（推荐结果依赖商品池）。 */
    public void evictProduct(String esin, Long userId) {
        if (!enabled) return;
        if (esin != null && !esin.isBlank()) delete("product:info:" + esin);
        if (userId != null) deletePattern("product:list:" + userId + ":*");
        deletePattern("recommend:*");
    }

    /** 术语变更：该用户该语言的术语缓存 + 全语言缓存。 */
    public void evictGlossary(Long userId, String language) {
        if (!enabled || userId == null) return;
        if (language != null && !language.isBlank())
            delete("glossary:user:" + userId + ":lang:" + language);
        delete("glossary:user:" + userId + ":all");
    }

    private void delete(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("缓存删除失败（忽略）key={}：{}", key, e.toString());
        }
    }

    private void deletePattern(String pattern) {
        List<String> keys = scan(pattern);
        if (keys.isEmpty()) return;
        try {
            redis.delete(keys);
            log.debug("缓存失效 pattern={} 删除 {} 个键", pattern, keys.size());
        } catch (Exception e) {
            log.warn("缓存批量删除失败（忽略）pattern={}：{}", pattern, e.toString());
        }
    }

    private List<String> scan(String pattern) {
        List<String> keys = new ArrayList<>();
        try {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            redis.execute(
                    (RedisCallback<Void>)
                            (RedisConnection connection) -> {
                                try (Cursor<byte[]> cursor =
                                        connection.keyCommands().scan(options)) {
                                    while (cursor.hasNext())
                                        keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                                }
                                return null;
                            });
        } catch (Exception e) {
            log.warn("缓存扫描失败（忽略）pattern={}：{}", pattern, e.toString());
        }
        return keys;
    }
}
