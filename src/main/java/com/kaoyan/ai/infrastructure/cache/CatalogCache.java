package com.kaoyan.ai.infrastructure.cache;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

/**
 * 公开只读聚合数据的 Redis 读缓存。
 *
 * <p>Redis 只承担读加速：写路径仍由数据库事务、乐观锁和幂等表保证一致性。 连不上 Redis 时自动降级为直查数据库（30 秒窗口），既不影响启动，也不影响业务正确性。
 */
@Component
public class CatalogCache {
  private static final Logger log = LoggerFactory.getLogger(CatalogCache.class);
  private static final String PREFIX = "kaoyan:v1:";
  private static final Duration DEGRADE_WINDOW = Duration.ofSeconds(30);

  private final StringRedisTemplate redis;
  private final boolean enabled;
  private final long ttlSeconds;
  private volatile long degradedUntil;

  public CatalogCache(
      StringRedisTemplate redis,
      @Value("${app.cache.redis-enabled:true}") boolean enabled,
      @Value("${app.cache.ttl-seconds:1800}") long ttlSeconds) {
    this.redis = redis;
    this.enabled = enabled;
    this.ttlSeconds = ttlSeconds;
  }

  public static String listKey(String table) {
    return PREFIX + table + ":public";
  }

  public static String itemKey(String table, String id) {
    return PREFIX + table + ":public:" + id;
  }

  public boolean enabled() {
    return enabled;
  }

  /** 预热前探活：Redis 没起时给出一条明确日志，而不是让业务侧误判。 */
  public boolean reachable() {
    if (!usable()) return false;
    try {
      redis.execute((RedisCallback<String>) connection -> connection.ping());
      return true;
    } catch (RuntimeException e) {
      degrade("探活", e);
      return false;
    }
  }

  private boolean usable() {
    return enabled && System.currentTimeMillis() >= degradedUntil;
  }

  public Optional<String> list(String table) {
    return read(listKey(table));
  }

  public Optional<String> item(String table, String id) {
    return read(itemKey(table, id));
  }

  public void putList(String table, String json) {
    write(listKey(table), json);
  }

  public void putItem(String table, String id, String json) {
    write(itemKey(table, id), json);
  }

  /** 预热用：一次 pipeline 写入一批 key，避免逐条往返。 */
  public void putAll(Map<String, String> entries) {
    if (!usable() || entries.isEmpty()) return;
    try {
      redis.executePipelined(
          (RedisCallback<Object>)
              connection -> {
                var serializer = redis.getStringSerializer();
                var expiration = Expiration.seconds(ttlSeconds);
                entries.forEach(
                    (key, value) ->
                        connection
                            .stringCommands()
                            .set(
                                serializer.serialize(key),
                                serializer.serialize(value),
                                expiration,
                                SetOption.upsert()));
                return null;
              });
    } catch (RuntimeException e) {
      degrade("批量写入", e);
    }
  }

  /** 写路径失效：同时删除单条 key 和列表 key，避免列表页读到旧数据。 */
  public void evict(String table, String id) {
    if (!usable()) return;
    try {
      redis.delete(java.util.List.of(itemKey(table, id), listKey(table)));
    } catch (RuntimeException e) {
      degrade("删除", e);
    }
  }

  public void evictList(String table) {
    if (!usable()) return;
    try {
      redis.delete(listKey(table));
    } catch (RuntimeException e) {
      degrade("删除", e);
    }
  }

  /** 多实例部署时只让一个实例执行预热。 */
  public boolean tryLock(String key, Duration ttl) {
    if (!usable()) return false;
    try {
      return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
    } catch (RuntimeException e) {
      degrade("加锁", e);
      return false;
    }
  }

  public void unlock(String key) {
    if (!usable()) return;
    try {
      redis.delete(key);
    } catch (RuntimeException e) {
      degrade("解锁", e);
    }
  }

  private Optional<String> read(String key) {
    if (!usable()) return Optional.empty();
    try {
      return Optional.ofNullable(redis.opsForValue().get(key));
    } catch (RuntimeException e) {
      degrade("读取", e);
      return Optional.empty();
    }
  }

  private void write(String key, String json) {
    if (!usable()) return;
    try {
      redis.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
    } catch (RuntimeException e) {
      degrade("写入", e);
    }
  }

  private void degrade(String operation, RuntimeException cause) {
    degradedUntil = System.currentTimeMillis() + DEGRADE_WINDOW.toMillis();
    log.warn(
        "Redis {}失败，{} 秒内降级为直查数据库：{}",
        operation,
        DEGRADE_WINDOW.toSeconds(),
        cause.getMessage());
  }
}