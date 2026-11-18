package com.kaoyan.ai.infrastructure.cache;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Component;

/**
 * 启动时验证 Redis 连接与读写。
 *
 * <p>Redis 是可选的缓存依赖：连不上时只记录告警，应用继续按数据库启动，避免本机没开 Redis 就无法进入本地联调。
 * 启动日志中的 “Redis 连接成功” 是判断配置是否生效的直接依据。
 */
@Component
public class RedisConnectivityCheck implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(RedisConnectivityCheck.class);
  private static final String PROBE_KEY = "health:startup";
  private static final Duration PROBE_TTL = Duration.ofSeconds(30);

  private final RedisCache cache;
  private final RedisProperties properties;

  public RedisConnectivityCheck(RedisCache cache, RedisProperties properties) {
    this.cache = cache;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    String address = properties.getHost() + ":" + properties.getPort();
    try {
      log.info("Redis 连接成功：{}，PING={}，读写校验通过", address, probe());
    } catch (RuntimeException ex) {
      log.warn(
          "Redis 连接不可用（{}）：{}。缓存不参与核心业务，应用按数据库继续启动。",
          address,
          rootMessage(ex));
    }
  }

  /** 写入、读回并删除一个短期探测键，确认不只是端口可连。 */
  private String probe() {
    String token = Long.toString(System.nanoTime());
    cache.put(PROBE_KEY, token, PROBE_TTL);
    try {
      String stored = cache.get(PROBE_KEY).orElse("");
      if (!token.equals(stored)) throw new IllegalStateException("写入后读取的值不一致");
      return cache.ping();
    } finally {
      cache.delete(PROBE_KEY);
    }
  }

  private static String rootMessage(Throwable ex) {
    Throwable root = ex;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    String message = root.getMessage();
    return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
  }
}
