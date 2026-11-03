package com.kaoyan.ai.persistence;

import static com.kaoyan.ai.common.Jsons.parse;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** event_outbox 专用存取；所有游标读取按 owner_id 隔离。 */
@Repository
public class EventOutboxRepository {
  public record Event(long id, String type, JsonNode payload) {}

  private final JdbcTemplate jdbc;

  public EventOutboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long reserve(String user, String type) {
    GeneratedKeyHolder key = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "INSERT INTO event_outbox(owner_id,event_type,payload) VALUES (?,?,?)",
                  new String[] {"id"});
          statement.setString(1, user);
          statement.setString(2, type);
          statement.setString(3, "{}");
          return statement;
        },
        key);
    if (key.getKey() == null) throw new IllegalStateException("事件主键未返回");
    return key.getKey().longValue();
  }

  public void complete(long id, String owner, JsonNode payload) {
    if (jdbc.update(
            "UPDATE event_outbox SET payload=? WHERE id=? AND owner_id=?",
            payload.toString(),
            id,
            owner)
        != 1) throw new IllegalStateException("事件记录不存在或归属不匹配");
  }

  public long latest(String user) {
    return jdbc.queryForObject(
        "SELECT COALESCE(MAX(id),0) FROM event_outbox WHERE owner_id=?", Long.class, user);
  }

  public List<Event> after(String user, long cursor) {
    return jdbc.query(
        "SELECT id,event_type,payload FROM event_outbox WHERE owner_id=? AND id>? ORDER BY id LIMIT"
            + " 100",
        (rs, n) -> new Event(rs.getLong(1), rs.getString(2), parse(rs.getString(3))),
        user,
        cursor);
  }
}
