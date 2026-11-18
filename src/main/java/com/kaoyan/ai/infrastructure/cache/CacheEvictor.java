package com.kaoyan.ai.infrastructure.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 事务提交之后再删缓存，避免并发读把旧值回填进 Redis。 */
@Component
public class CacheEvictor {
  private final CatalogCache cache;

  public CacheEvictor(CatalogCache cache) {
    this.cache = cache;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(AggregateChanged event) {
    cache.evict(event.table(), event.id());
  }
}