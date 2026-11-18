package com.kaoyan.ai.service;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.infrastructure.cache.CatalogCache;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  private final RecordRepository db;
  private final CatalogCache cache;

  public CatalogService(RecordRepository db, CatalogCache cache) {
    this.db = db;
    this.cache = cache;
  }

  /** 公开只读列表：先查 Redis，未命中回源数据库并回填；Redis 不可用时自动降级。 */
  public List<ObjectNode> records(String table) {
    Optional<String> cached = cache.list(table);
    if (cached.isPresent()) return parseObjects(cached.get());
    List<ObjectNode> rows = db.list(table, "public");
    cache.putList(table, MAPPER.valueToTree(rows).toString());
    return rows;
  }

  public ObjectNode program(String id) {
    return cachedPublicGet("programs", id);
  }

  private ObjectNode cachedPublicGet(String table, String id) {
    Optional<String> cached = cache.item(table, id);
    if (cached.isPresent()) return (ObjectNode) parse(cached.get());
    ObjectNode value = db.publicGet(table, id);
    cache.putItem(table, id, value.toString());
    return value;
  }

  private static List<ObjectNode> parseObjects(String json) {
    List<ObjectNode> rows = new ArrayList<>();
    parse(json).forEach(node -> rows.add((ObjectNode) node));
    return rows;
  }

  public ObjectNode subject(String id) {
    for (ObjectNode t : records("knowledge_trees"))
      if (t.path("subjectId").asText().equals(id)) return t;
    throw ApiException.missing();
  }

  public ObjectNode knowledge(String id) {
    for (ObjectNode t : records("knowledge_trees"))
      for (JsonNode n : t.path("nodes"))
        if (n.path("id").asText().equals(id)) return (ObjectNode) n;
    throw ApiException.missing();
  }

  public ObjectNode questionVersion(String id) {
    return cachedPublicGet("questions", id);
  }

  public ObjectNode displayQuestion(JsonNode stored) {
    ObjectNode question = (ObjectNode) stored.path("question").deepCopy();
    if (!question.hasNonNull("difficultyGrade"))
      question.set("difficultyGrade", difficultyGrade(question.path("difficulty").asText()));
    return question;
  }

  static JsonNode difficultyGrade(String text) {
    return switch (text.strip().toLowerCase(Locale.ROOT)) {
      case "easy", "简单" -> MAPPER.valueToTree("easy");
      case "medium", "中等" -> MAPPER.valueToTree("medium");
      case "hard", "困难" -> MAPPER.valueToTree("hard");
      default -> null;
    };
  }

  public List<ObjectNode> questions(String subject, String knowledge, String difficulty) {
    return records("questions").stream()
        .filter(q -> q.path("question").path("subjectId").asText().equals(subject))
        .filter(
            q ->
                knowledge == null
                    || knowledge.isEmpty()
                    || q.path("question").path("knowledgeId").asText().equals(knowledge))
        .filter(
            q ->
                difficulty == null
                    || difficulty.isEmpty()
                    || q.path("question").path("difficulty").asText().equals(difficulty)
                    || difficulty.equals(grade(q.path("question"))))
        .map(this::displayQuestion)
        .toList();
  }

  private String grade(JsonNode question) {
    if (question.hasNonNull("difficultyGrade")) return question.path("difficultyGrade").asText();
    JsonNode grade = difficultyGrade(question.path("difficulty").asText());
    return grade == null ? "" : grade.asText();
  }

  public ObjectNode facts(String id, int year) {
    ObjectNode p = program(id);
    require(p.path("year").asInt() == year, "该院校快照不属于所选年份");
    ArrayNode metrics = arr(), missing = arr();
    for (String k : List.of("scoreLine", "enrollment", "admissionRatio")) {
      JsonNode value = p.get(k);
      if (value.isNull()) missing.add(k);
      metrics.add(
          obj(
              "type",
              k,
              "year",
              year,
              "value",
              value,
              "unit",
              k.equals("scoreLine") ? "points" : k.equals("enrollment") ? "persons" : "ratio",
              "sources",
              p.get("sources")));
    }
    return obj("program", p, "metrics", metrics, "missingFields", missing);
  }

  public JsonNode execute(String op, DomainContext c) {
    JsonNode q = c.query(), b = c.body();
    return switch (op) {
      case "op019" ->
          page(
              records("programs").stream()
                  .filter(p -> p.path("year").asInt() == q.path("year").asInt())
                  .filter(p -> !q.has("major") || p.path("major").asText().contains(c.q("major")))
                  .filter(p -> !q.has("region") || p.path("region").asText().equals(c.q("region")))
                  .filter(
                      p ->
                          !q.has("difficulty")
                              || p.path("difficulty").asText().equals(c.q("difficulty")))
                  .filter(
                      p ->
                          !q.has("score")
                              || p.path("scoreLine").isNull()
                              || p.path("scoreLine").asDouble() <= q.path("score").asDouble())
                  .toList(),
              q);
      case "op020" ->
          SchoolRecommendations.recommend(b, records("programs"), db.publicProgramOriginals());
      case "op021" -> {
        ObjectNode f = facts(c.path("id"), q.path("year").asInt());
        if (q.has("metricTypes")) {
          ArrayNode filtered = arr();
          for (JsonNode x : f.path("metrics"))
            for (JsonNode k : q.path("metricTypes"))
              if (k.asText().equals(x.path("type").asText())) filtered.add(x);
          f.set("metrics", filtered);
        }
        yield f;
      }
      case "op022" -> {
        ArrayNode f = arr();
        for (JsonNode id : b.path("programIds")) f.add(facts(id.asText(), c.integer("year")));
        yield obj("year", c.integer("year"), "programs", f);
      }
      case "op024" -> db.get("evaluations", c.path("id"), c.user());
      case "op025" -> {
        String key = c.q("subject") + ":" + c.q("examType") + ":" + c.q("year");
        yield cachedPublicGet("knowledge_trees", key);
      }
      case "op027" -> {
        List<JsonNode> points = new ArrayList<>();
        for (ObjectNode t : records("knowledge_trees"))
          if (t.path("category").asText().equals(c.q("subject")))
            for (JsonNode n : t.path("nodes"))
              points.add(
                  obj(
                      "knowledgeId",
                      n.get("id"),
                      "title",
                      n.get("name"),
                      "type",
                      c.q("type"),
                      "content",
                      "结合课程资料复习定义、条件、典型题和常见误区。",
                      "sources",
                      t.get("sources")));
        yield obj("items", points);
      }
      case "op031" -> {
        program(c.q("programId"));
        yield obj(
            "items",
            records("subjects").stream()
                .filter(
                    x ->
                        x.path("discipline").asText().equals(c.q("discipline"))
                            && x.path("programId").asText().equals(c.q("programId"))
                            && x.path("year").asInt() == q.path("year").asInt())
                .toList());
      }
      case "op032" -> {
        ObjectNode tree = subject(c.q("subjectId"));
        require(tree.path("edition").asText().equals(c.q("bookEdition")), "参考书版本不匹配");
        yield publicTree(tree);
      }
      case "op034" ->
          page(
              questions(c.q("subjectId"), c.q("knowledgeId"), null).stream()
                  .filter(
                      x ->
                          !q.has("year")
                              || questionVersion(x.path("versionId").asText()).path("year").asInt()
                                  == q.path("year").asInt())
                  .toList(),
              q);
      case "op057" ->
          page(
              records("papers").stream()
                  .filter(
                      x ->
                          x.path("paper").path("subject").asText().equals(c.q("subject"))
                              && x.path("paper").path("examType").asText().equals(c.q("examType")))
                  .map(x -> (ObjectNode) x.path("paper"))
                  .filter(x -> !q.has("year") || x.path("year").asInt() == q.path("year").asInt())
                  .toList(),
              q);
      case "op070" -> {
        program(c.q("programId"));
        yield obj(
            "type",
            c.q("type"),
            "programId",
            c.q("programId"),
            "title",
            c.q("type").equals("etiquette") ? "复试礼仪" : "复试流程",
            "content",
            c.q("type").equals("etiquette")
                ? "提前到场，仪表整洁；倾听问题，回答条理清晰；不确定时坦诚说明；结束时礼貌致谢。"
                : "核对院校官方通知与材料要求，按通知参加资格审核、专业面试和外语测试；具体安排以院校通知为准。",
            "sources",
            arr());
      }
      default -> throw new IllegalArgumentException(op);
    };
  }

  public ObjectNode publicTree(ObjectNode t) {
    ObjectNode out = t.deepCopy();
    out.remove("category");
    out.set("nodes", MAPPER.valueToTree(learningNodes(t)));
    return out;
  }

  public List<ObjectNode> learningNodes(JsonNode tree) {
    Map<String, ObjectNode> nodes = new LinkedHashMap<>();
    for (JsonNode item : tree.path("nodes")) {
      ObjectNode node = item.deepCopy();
      nodes.put(node.path("id").asText(), node);
    }
    Map<String, Set<String>> edges = new LinkedHashMap<>();
    for (var entry : nodes.entrySet()) {
      String id = entry.getKey();
      Set<String> prerequisites = new LinkedHashSet<>();
      for (JsonNode prerequisite : entry.getValue().path("prerequisiteIds")) {
        String target = prerequisite.asText();
        if (!id.equals(target) && nodes.containsKey(target)
            && !reaches(target, id, edges, new HashSet<>())) prerequisites.add(target);
      }
      edges.put(id, prerequisites);
    }
    for (var entry : nodes.entrySet()) {
      ObjectNode node = entry.getValue();
      node.set("prerequisiteIds", MAPPER.valueToTree(edges.get(entry.getKey())));
      if (!node.hasNonNull("level")) node.put("level", node.path("depth").asInt() + 1);
    }
    return List.copyOf(nodes.values());
  }

  private boolean reaches(String from, String target, Map<String, Set<String>> edges, Set<String> visited) {
    if (from.equals(target)) return true;
    if (!visited.add(from)) return false;
    for (String prerequisite : edges.getOrDefault(from, Set.of()))
      if (reaches(prerequisite, target, edges, visited)) return true;
    return false;
  }
}
