package com.kaoyan.ai.persistence.repository;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.infrastructure.cache.AggregateChanged;
import com.kaoyan.ai.persistence.entity.*;
import com.kaoyan.ai.persistence.mapper.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

/** 聚合持久化适配层：保留既有 JSON、归属约束、乐观锁和事务语义。 */
@Repository
public class RecordRepository {
  public static final Set<String> TABLES = AggregateTable.NAMES;
  private static final String PUBLIC = "public";
  private final AggregateMapper mapper;
  private final UserAccountMapper users;
  private final ApplicationEventPublisher events;

  public RecordRepository(
      AggregateMapper mapper, UserAccountMapper users, ApplicationEventPublisher events) {
    this.mapper = mapper;
    this.users = users;
    this.events = events;
  }

  public void create(String table, String id, String owner, JsonNode payload) {
    mapper.insert(
        new AggregateTable(table),
        new AggregateRecord(id, owner, payload.path("version").asInt(1), payload.toString()));
    publishChanged(table, id, owner);
  }

  public ObjectNode get(String table, String id, String owner) {
    String payload = mapper.findPayload(new AggregateTable(table), id, owner);
    if (payload == null) throw ApiException.missing();
    return (ObjectNode) parse(payload);
  }

  public ObjectNode publicGet(String table, String id) {
    return get(table, id, "public");
  }

  public Optional<ObjectNode> find(String table, String id, String owner) {
    String payload = mapper.findPayload(new AggregateTable(table), id, owner);
    return payload == null ? Optional.empty() : Optional.of((ObjectNode) parse(payload));
  }

  public List<ObjectNode> list(String table, String owner) {
    return mapper.findByOwner(new AggregateTable(table), owner).stream()
        .map(value -> (ObjectNode) parse(value))
        .toList();
  }

  public Map<String, JsonNode> publicProgramOriginals() {
    Map<String, JsonNode> originals = new HashMap<>();
    for (String payload : mapper.findPublicProgramOriginals()) {
      JsonNode original = parse(payload);
      originals.put(original.path("id").asText(), original);
    }
    return originals;
  }

  public int version(String table, String id, String owner) {
    Integer version = mapper.findVersion(new AggregateTable(table), id, owner);
    if (version == null) throw ApiException.missing();
    return version;
  }

  public void update(String table, String id, String owner, ObjectNode data, int expected) {
    int changed =
        mapper.update(
            new AggregateTable(table),
            new AggregateRecord(
                id, owner, data.path("version").asInt(expected + 1), data.toString()),
            expected);
    if (changed != 1) throw new ApiException(409, "VERSION_CONFLICT", "资源已被更新，请刷新");
    publishChanged(table, id, owner);
  }

  public void replace(String table, String id, String owner, ObjectNode data) {
    update(table, id, owner, data, version(table, id, owner));
  }

  public void delete(String table, String id, String owner) {
    if (mapper.delete(new AggregateTable(table), id, owner) != 1) throw ApiException.missing();
    publishChanged(table, id, owner);
  }

  /** 只有公开数据进缓存，用户维度数据不做预热也不发失效事件。 */
  private void publishChanged(String table, String id, String owner) {
    if (PUBLIC.equals(owner)) events.publishEvent(new AggregateChanged(table, id, owner));
  }

  public void lockOwner(String user) {
    if (users.lockById(user) == null) throw ApiException.missing();
  }

  /** 仅供后台调度和匿名排名跨用户扫描；普通用户查询必须使用 get/list。 */
  public List<ObjectNode> scan(String table, String ownerField, String payloadField) {
    return mapper.scan(new AggregateTable(table)).stream()
        .map(row -> obj(ownerField, row.ownerId(), payloadField, parse(row.payload())))
        .toList();
  }

  public List<ObjectNode> queuedJobs() {
    return mapper.findQueuedJobs().stream()
        .map(row -> obj("owner", row.ownerId(), "job", parse(row.payload())))
        .toList();
  }

  public List<String> userIds() {
    return users.findAllIds();
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
