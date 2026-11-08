package com.kaoyan.ai.persistence.repository;

import com.kaoyan.ai.persistence.entity.IdempotencyEntry;
import com.kaoyan.ai.persistence.mapper.IdempotencyMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 幂等响应与业务数据由同一 Spring 事务提交。 */
@Repository
public class IdempotencyRepository {
  private final IdempotencyMapper mapper;

  public IdempotencyRepository(IdempotencyMapper mapper) {
    this.mapper = mapper;
  }

  public Optional<IdempotencyEntry> find(String user, String operation, String key) {
    return Optional.ofNullable(mapper.find(user, operation, key));
  }

  public void save(
      String user, String operation, String key, String fingerprint, int status, String response) {
    mapper.insert(user, operation, key, fingerprint, status, response);
  }

  public void delete(String user, String operation, String key) {
    mapper.delete(user, operation, key);
  }
}
