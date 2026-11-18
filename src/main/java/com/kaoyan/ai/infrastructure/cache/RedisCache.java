package com.kaoyan.ai.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存访问入口。
 *
 * <p>键统一加业务前缀，避免与同一 Redis 实例上的其他应用互相覆盖；值以字符串存取，复杂结构由调用方先序列化为
 * JSON。Redis 只作为可选缓存：连接失败时调用方应回退到数据库，不得把缓存当作唯一数据源，也不要在 Redis 中保存
 * 密码、Cookie、JWT 或学生隐私原文。
 */
@Component
public class RedisCache {
  private final StringRedisTemplate redis;
  private final String prefix;

  public RedisCache(
      StringRedisTemplate redis, @Value("${app.cache.key-prefix:kaoyan-ai:}") String prefix) {
    this.redis = redis;
    this.prefix = prefix;
  }

  /** 加上应用前缀后的完整键。 */
  public String key(String name) {
    return prefix + name;
  }

  public void put(String name, String value, Duration ttl) {
    redis.opsForValue().set(key(name), value, ttl);
  }

  public Optional<String> get(String name) {
    return Optional.ofNullable(redis.opsForValue().get(key(name)));
  }

  public boolean delete(String name) {
    return Boolean.TRUE.equals(redis.delete(key(name)));
  }

  /** 用于连通性检查；返回值正常为 PONG。 */
  public String ping() {
    return redis.execute((RedisCallback<String>) connection -> connection.ping());
  }
}
