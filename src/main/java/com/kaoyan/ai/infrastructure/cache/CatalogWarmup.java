package com.kaoyan.ai.infrastructure.cache;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.Jsons;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动后预热公开只读数据。
 *
 * <p>放在 {@link ApplicationReadyEvent} 之后执行，确保 Flyway 迁移和 {@code LocalDataInitializer}
 * 的种子数据都已就绪；预热在独立线程里跑，不拖慢启动。预热失败只记日志，业务继续走数据库。
 */
@Component
public class CatalogWarmup {
  private static final Logger log = LoggerFactory.getLogger(CatalogWarmup.class);
  private static final String LOCK_KEY = "kaoyan:v1:warmup:lock";

  private final RecordRepository db;
  private final CatalogCache cache;
  private final List<String> tables;

  public CatalogWarmup(
      RecordRepository db,
      CatalogCache cache,
      @Value("${app.cache.warmup-tables:}") String warmupTables) {
    this.db = db;
    this.cache = cache;
    this.tables =
        Arrays.stream(warmupTables.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void schedule() {
    if (!cache.enabled()) {
      log.info("Redis 缓存已关闭（app.cache.redis-enabled=false），跳过预热");
      return;
    }
    if (tables.isEmpty()) {
      log.info("未配置预热表（app.cache.warmup-tables），跳过预热");
      return;
    }
    Thread worker = new Thread(this::warm, "catalog-warmup");
    worker.setDaemon(true);
    worker.start();
  }

  void warm() {
    if (!cache.reachable()) {
      log.warn("Redis 不可用，本次跳过预热；业务继续直查数据库，装好 Redis 并重启后自动预热");
      return;
    }
    if (!cache.tryLock(LOCK_KEY, Duration.ofMinutes(5))) {
      log.info("已有实例正在预热同一份缓存，跳过本次预热");
      return;
    }
    long startedAt = System.currentTimeMillis();
    int written = 0;
    try {
      for (String table : tables) {
        List<ObjectNode> rows = db.list(table, "public");
        Map<String, String> batch = new LinkedHashMap<>();
        batch.put(CatalogCache.listKey(table), Jsons.MAPPER.writeValueAsString(rows));
        for (ObjectNode row : rows) {
          batch.put(CatalogCache.itemKey(table, row.path("id").asText()), row.toString());
        }
        cache.putAll(batch);
        written += batch.size();
      }
      log.info(
          "缓存预热完成：{} 张表 / {} 个 key，耗时 {} ms",
          tables.size(),
          written,
          System.currentTimeMillis() - startedAt);
    } catch (Exception e) {
      log.warn("缓存预热失败，业务继续直查数据库：{}", e.getMessage());
    } finally {
      cache.unlock(LOCK_KEY);
    }
  }
}