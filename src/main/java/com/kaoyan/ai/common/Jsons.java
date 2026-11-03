package com.kaoyan.ai.common;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;

/** 契约 DTO 的 JSON 构造器；业务实体中的私密字段不进入 DTO。 */
public final class Jsons {
  public static final ObjectMapper MAPPER = new ObjectMapper();
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  private Jsons() {}

  public static ObjectNode obj(Object... pairs) {
    ObjectNode n = MAPPER.createObjectNode();
    for (int i = 0; i < pairs.length; i += 2)
      n.set(pairs[i].toString(), MAPPER.valueToTree(pairs[i + 1]));
    return n;
  }

  public static ArrayNode arr(Object... values) {
    return MAPPER.valueToTree(Arrays.asList(values));
  }

  public static JsonNode parse(String s) {
    try {
      return MAPPER.readTree(s);
    } catch (Exception e) {
      throw new IllegalStateException("持久化 JSON 无法解析", e);
    }
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static String now() {
    return Instant.now().toString();
  }

  public static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  public static String text(JsonNode n, String k) {
    return n.path(k).asText();
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new ApiException(422, "VALIDATION_FAILED", message);
  }

  public static void version(JsonNode n, int expected) {
    if (n.path("version").asInt() != expected)
      throw new ApiException(409, "VERSION_CONFLICT", "数据已变化，请刷新后重试");
  }

  public static ObjectNode page(List<? extends JsonNode> list, JsonNode q) {
    int p = q.path("page").asInt(1), s = q.path("pageSize").asInt(20);
    int start = Math.min(list.size(), (p - 1) * s);
    return obj(
        "items",
        list.subList(start, Math.min(list.size(), start + s)),
        "page",
        p,
        "pageSize",
        s,
        "total",
        list.size());
  }
}
