package com.kaoyan.ai.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Redis 真实连接测试：本机未启动 Redis 时跳过，其余测试仍只用 H2，不依赖外部服务。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:rediscache;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.seed-local=false"
    })
class RedisCacheTests {
  @Autowired private RedisCache cache;

  @Test
  void keysCarryApplicationPrefix() {
    assertTrue(cache.key("session:1").startsWith("kaoyan-ai:"));
  }

  @Test
  void setGetDeleteRoundTripOnLiveRedis() {
    Assumptions.assumeTrue(redisUsable(), "本机未启动 Redis 或未配置密码，跳过真实连接测试");
    String name = "test:cache:" + System.nanoTime();
    String value = "value-" + name;
    cache.put(name, value, Duration.ofSeconds(30));
    assertEquals(value, cache.get(name).orElseThrow());
    assertTrue(cache.delete(name));
    assertTrue(cache.get(name).isEmpty());
  }

  /** 端口可连还不够：密码不对时 PING 会抛异常，此时跳过而不是让测试失败。 */
  private boolean redisUsable() {
    try {
      cache.ping();
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }
}
