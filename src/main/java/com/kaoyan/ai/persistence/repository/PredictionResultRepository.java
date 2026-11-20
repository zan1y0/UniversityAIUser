package com.kaoyan.ai.persistence.repository;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 记录实际对外返回的数据；缓存命中单独标记，统计时可区分请求与推理次数。 */
@Repository
public class PredictionResultRepository {
  private final JdbcTemplate jdbc;

  public PredictionResultRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(String user, String subject, String operation, String version,
      long latency, boolean cacheHit, JsonNode result) {
    jdbc.update("INSERT INTO kt_prediction_results "
        + "(id,user_id,subject_id,operation_id,algorithm_version,latency_ms,cache_hit,result_json) "
        + "VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(), user, subject, operation,
        version, latency, cacheHit, result.toString());
  }
}
