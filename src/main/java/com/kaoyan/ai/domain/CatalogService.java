package com.kaoyan.ai.domain;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.RecordRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  private final RecordRepository db;

  public CatalogService(RecordRepository db) {
    this.db = db;
  }

  public List<ObjectNode> records(String table) {
    return db.list(table, "public");
  }

  public ObjectNode program(String id) {
    return db.publicGet("programs", id);
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
    return db.publicGet("questions", id);
  }

  public ObjectNode displayQuestion(JsonNode stored) {
    return (ObjectNode) stored.path("question").deepCopy();
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
                    || q.path("question").path("difficulty").asText().equals(difficulty))
        .map(this::displayQuestion)
        .toList();
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
      case "op020" -> {
        List<JsonNode> list = new ArrayList<>();
        for (ObjectNode p : records("programs")) {
          boolean region = false;
          for (JsonNode r : b.path("regions"))
            if (r.asText().equals(p.path("region").asText())) region = true;
          if (region
              && p.path("year").asInt() == c.integer("year")
              && p.path("difficulty").asText().equals(c.b("difficulty"))) {
            double score =
                p.path("scoreLine").isNull()
                    ? 50
                    : Math.max(
                        0,
                        Math.min(
                            100,
                            70
                                + (b.path("estimatedScore").asDouble()
                                        - p.path("scoreLine").asDouble())
                                    / 3));
            list.add(
                obj(
                    "program",
                    p,
                    "matchScore",
                    score,
                    "reasons",
                    arr("根据目标地区、难度与分数匹配"),
                    "risks",
                    arr("匹配分不等于录取概率，请以当年官方招生信息为准")));
          }
        }
        list.sort(Comparator.comparingDouble(x -> -x.path("matchScore").asDouble()));
        yield obj("items", list);
      }
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
        yield db.publicGet("knowledge_trees", key);
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
        ObjectNode tree = subject(c.q("subjectId")).deepCopy();
        require(tree.path("edition").asText().equals(c.q("bookEdition")), "参考书版本不匹配");
        tree.remove("category");
        yield tree;
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
    return out;
  }
}
