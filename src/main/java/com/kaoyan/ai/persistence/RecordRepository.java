package com.kaoyan.ai.persistence;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 每个聚合使用独立表。版本列用于 CAS，DTO 快照持久化为 JSON 文本。 */
@Repository
public class RecordRepository {
  public static final Set<String> TABLES =
      Set.of(
          "profiles",
          "avatar_preferences",
          "reminder_preferences",
          "checkins",
          "programs",
          "knowledge_trees",
          "subjects",
          "books",
          "questions",
          "papers",
          "evaluations",
          "conversations",
          "qa_messages",
          "recognitions",
          "practice_sessions",
          "attempts",
          "wrong_items",
          "plans",
          "exam_sessions",
          "exam_submissions",
          "exam_results",
          "score_snapshots",
          "interviews",
          "interview_turns",
          "interview_reports",
          "study_events",
          "notices",
          "notice_revisions",
          "news_follows",
          "milestones",
          "notifications",
          "jobs",
          "files",
          "job_commands");
  private final JdbcTemplate jdbc;

  public RecordRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  private String table(String t) {
    if (!TABLES.contains(t)) throw new IllegalArgumentException("未知聚合表");
    return t;
  }

  public void create(String t, String id, String owner, JsonNode payload) {
    jdbc.update(
        "INSERT INTO "
            + table(t)
            + " (id,owner_id,version,payload,created_at,updated_at) VALUES"
            + " (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id,
        owner,
        payload.path("version").asInt(1),
        payload.toString());
  }

  public ObjectNode get(String t, String id, String owner) {
    var l =
        jdbc.query(
            "SELECT payload FROM " + table(t) + " WHERE id=? AND owner_id=?",
            (rs, n) -> (ObjectNode) parse(rs.getString(1)),
            id,
            owner);
    if (l.isEmpty()) throw ApiException.missing();
    return l.get(0);
  }

  public ObjectNode publicGet(String t, String id) {
    return get(t, id, "public");
  }

  public Optional<ObjectNode> find(String t, String id, String owner) {
    try {
      return Optional.of(get(t, id, owner));
    } catch (ApiException e) {
      if (e.status() == 404) return Optional.empty();
      throw e;
    }
  }

  public List<ObjectNode> list(String t, String owner) {
    return jdbc.query(
        "SELECT payload FROM " + table(t) + " WHERE owner_id=? ORDER BY created_at,id",
        (rs, n) -> (ObjectNode) parse(rs.getString(1)),
        owner);
  }

  public void update(String t, String id, String owner, ObjectNode data, int expected) {
    int changed =
        jdbc.update(
            "UPDATE "
                + table(t)
                + " SET payload=?,version=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner_id=?"
                + " AND version=?",
            data.toString(),
            data.path("version").asInt(expected + 1),
            id,
            owner,
            expected);
    if (changed != 1) throw new ApiException(409, "VERSION_CONFLICT", "资源已被更新，请刷新");
  }

  public void replace(String t, String id, String owner, ObjectNode data) {
    var v =
        jdbc.queryForObject(
            "SELECT version FROM " + table(t) + " WHERE id=? AND owner_id=?",
            Integer.class,
            id,
            owner);
    update(t, id, owner, data, v);
  }

  public void delete(String t, String id, String owner) {
    if (jdbc.update("DELETE FROM " + table(t) + " WHERE id=? AND owner_id=?", id, owner) != 1)
      throw ApiException.missing();
  }

  public void lockOwner(String user) {
    jdbc.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE", String.class, user);
  }

  public String fingerprint(JsonNode n) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(n.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
