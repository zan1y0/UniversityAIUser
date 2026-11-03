package com.kaoyan.ai.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 以用户、操作和幂等键为联合主键，保存原始响应而非重新计算响应。 */
@Repository
public class IdempotencyRepository {
  public record Entry(String fingerprint, int statusCode, String responseBody, Instant createdAt) {}

  private final JdbcTemplate jdbc;

  public IdempotencyRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Entry> find(String user, String operation, String key) {
    return jdbc
        .query(
            "SELECT fingerprint,status_code,response_body,created_at FROM idempotency_records WHERE"
                + " user_id=? AND operation_id=? AND idem_key=?",
            (rs, n) ->
                new Entry(
                    rs.getString(1), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
            user,
            operation,
            key)
        .stream()
        .findFirst();
  }

  public void save(
      String user, String operation, String key, String fingerprint, int status, String response) {
    jdbc.update(
        "INSERT INTO"
            + " idempotency_records(user_id,operation_id,idem_key,fingerprint,status_code,response_body)"
            + " VALUES (?,?,?,?,?,?)",
        user,
        operation,
        key,
        fingerprint,
        status,
        response);
  }

  public void delete(String user, String operation, String key) {
    jdbc.update(
        "DELETE FROM idempotency_records WHERE user_id=? AND operation_id=? AND idem_key=?",
        user,
        operation,
        key);
  }
}
