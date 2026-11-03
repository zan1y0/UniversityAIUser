package com.kaoyan.ai.domain;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.RecordRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PracticeService {
  private final RecordRepository db;
  private final CatalogService catalog;
  private final com.kaoyan.ai.ai.ModelGateway model;

  public PracticeService(
      RecordRepository db, CatalogService catalog, com.kaoyan.ai.ai.ModelGateway model) {
    this.db = db;
    this.catalog = catalog;
    this.model = model;
  }

  public void validateAnswer(JsonNode question, JsonNode answer) {
    require(question.path("kind").asText().equals(answer.path("kind").asText()), "作答类型与题目不匹配");
    Set<String> options = new HashSet<>();
    for (JsonNode o : question.path("options")) options.add(o.path("id").asText());
    if (answer.has("optionId"))
      require(options.contains(answer.path("optionId").asText()), "选项不属于该题");
    if (answer.has("optionIds"))
      for (JsonNode v : answer.path("optionIds")) require(options.contains(v.asText()), "选项不属于该题");
  }

  public boolean correct(JsonNode expected, JsonNode answer) {
    String kind = expected.path("kind").asText();
    return switch (kind) {
      case "numeric" ->
          new BigDecimal(expected.path("value").asText())
                  .compareTo(new BigDecimal(answer.path("value").asText()))
              == 0;
      case "multipleChoice" -> {
        Set<String> a = new HashSet<>(), b = new HashSet<>();
        expected.path("optionIds").forEach(x -> a.add(x.asText()));
        answer.path("optionIds").forEach(x -> b.add(x.asText()));
        yield a.equals(b);
      }
      case "text" ->
          expected.path("content").asText().strip().equals(answer.path("content").asText().strip());
      default -> expected.equals(answer);
    };
  }

  public JsonNode execute(String op, DomainContext c) {
    String user = c.user();
    JsonNode b = c.body(), q = c.query();
    return switch (op) {
      case "op043" -> {
        catalog.subject(c.b("subjectId"));
        String kid = b.path("knowledgeId").isNull() ? null : c.b("knowledgeId");
        if (kid != null)
          require(
              catalog.knowledge(kid).path("subjectId").asText().equals(c.b("subjectId")),
              "知识点不属于该科目");
        List<ObjectNode> all =
            new ArrayList<>(catalog.questions(c.b("subjectId"), kid, c.b("difficulty")));
        require(!all.isEmpty(), "题库中暂无符合条件的题目");
        Collections.shuffle(all);
        int size = Math.min(all.size(), c.b("type").equals("daily") ? 5 : 20);
        ObjectNode s =
            obj(
                "id",
                id(),
                "type",
                c.b("type"),
                "subjectId",
                c.b("subjectId"),
                "knowledgeId",
                kid,
                "difficulty",
                c.b("difficulty"),
                "questions",
                all.subList(0, size),
                "createdAt",
                now());
        db.create("practice_sessions", s.path("id").asText(), user, s);
        yield s;
      }
      case "op044" -> db.get("practice_sessions", c.path("id"), user);
      case "op045" -> {
        ObjectNode session = db.get("practice_sessions", c.path("id"), user);
        ObjectNode selected = null;
        for (JsonNode x : session.path("questions"))
          if (x.path("versionId").asText().equals(c.b("questionVersionId")))
            selected = (ObjectNode) x;
        require(selected != null, "题目不属于当前练习会话");
        validateAnswer(selected, b.path("answer"));
        ObjectNode stored = catalog.questionVersion(c.b("questionVersionId"));
        double awarded;
        boolean ok;
        if (selected.path("kind").asText().equals("text")) {
          JsonNode feedback =
              model.withSchema(
                  "ScoreDimension",
                  "按参考答案评分：题目 "
                      + selected
                      + "，学生作答 "
                      + b.path("answer")
                      + "，参考答案 "
                      + stored.path("correctAnswer")
                      + "；maxScore="
                      + selected.path("maxScore").asDouble());
          awarded = feedback.path("score").asDouble();
          require(awarded <= selected.path("maxScore").asDouble(), "模型评分超过题目满分");
          ok = awarded >= selected.path("maxScore").asDouble() * 0.6;
        } else {
          ok = correct(stored.path("correctAnswer"), b.path("answer"));
          awarded = ok ? selected.path("maxScore").asDouble() : 0;
        }
        String wrongId = null;
        ObjectNode wrong = null;
        for (ObjectNode x : db.list("wrong_items", user))
          if (x.path("question").path("versionId").asText().equals(c.b("questionVersionId")))
            wrong = x;
        if (!ok || wrong != null) {
          if (wrong == null) {
            wrongId = id();
            wrong =
                obj(
                    "id",
                    wrongId,
                    "question",
                    selected,
                    "attemptCount",
                    1,
                    "lastAttemptAt",
                    now(),
                    "mastered",
                    false,
                    "knowledgeId",
                    selected.get("knowledgeId"),
                    "version",
                    1);
            db.create("wrong_items", wrongId, user, wrong);
          } else {
            wrongId = wrong.path("id").asText();
            int v = wrong.path("version").asInt();
            wrong.put("attemptCount", wrong.path("attemptCount").asInt() + 1);
            wrong.put("lastAttemptAt", now());
            wrong.put("mastered", ok);
            wrong.put("version", v + 1);
            db.update("wrong_items", wrongId, user, wrong, v);
          }
        }
        ObjectNode attempt =
            obj(
                "id",
                id(),
                "sessionId",
                session.get("id"),
                "questionVersionId",
                c.b("questionVersionId"),
                "answer",
                b.get("answer"),
                "correct",
                ok,
                "score",
                awarded,
                "maxScore",
                selected.get("maxScore"),
                "correctAnswer",
                stored.get("correctAnswer"),
                "explanation",
                stored.get("explanation"),
                "wrongItemId",
                wrongId,
                "createdAt",
                now());
        db.create("attempts", attempt.path("id").asText(), user, attempt);
        yield attempt;
      }
      case "op046" ->
          page(
              db.list("wrong_items", user).stream()
                  .filter(
                      x ->
                          !q.has("subjectId")
                              || x.path("question")
                                  .path("subjectId")
                                  .asText()
                                  .equals(c.q("subjectId")))
                  .filter(
                      x ->
                          !q.has("knowledgeId")
                              || x.path("knowledgeId").asText().equals(c.q("knowledgeId")))
                  .toList(),
              q);
      case "op047" -> {
        ObjectNode w = db.get("wrong_items", c.path("id"), user);
        yield page(
            db.list("attempts", user).stream()
                .filter(
                    x ->
                        x.path("questionVersionId")
                            .asText()
                            .equals(w.path("question").path("versionId").asText()))
                .toList(),
            q);
      }
      case "op048" -> {
        ObjectNode k = catalog.knowledge(c.q("knowledgeId"));
        yield obj(
            "items",
            catalog
                .questions(k.path("subjectId").asText(), c.q("knowledgeId"), c.q("difficulty"))
                .stream()
                .filter(x -> !x.path("id").asText().equals(c.q("questionId")))
                .toList());
      }
      case "op049" -> {
        catalog.subject(c.q("subjectId"));
        ArrayNode items = arr();
        LocalDate since =
            c.q("timeRange").equals("week")
                ? today().minusDays(6)
                : c.q("timeRange").equals("month")
                    ? today().minusDays(29)
                    : LocalDate.of(2000, 1, 1);
        List<ObjectNode> attempts = db.list("attempts", user);
        for (JsonNode k : catalog.subject(c.q("subjectId")).path("nodes")) {
          int n = 0, ok = 0;
          for (ObjectNode a : attempts) {
            ObjectNode question = catalog.questionVersion(a.path("questionVersionId").asText());
            if (question.path("question").path("knowledgeId").asText().equals(k.path("id").asText())
                && !Instant.parse(a.path("createdAt").asText())
                    .atZone(ZONE)
                    .toLocalDate()
                    .isBefore(since)) {
              n++;
              if (a.path("correct").asBoolean()) ok++;
            }
          }
          items.add(
              obj(
                  "knowledgeId",
                  k.get("id"),
                  "attemptCount",
                  n,
                  "correctCount",
                  ok,
                  "mastery",
                  n == 0 ? null : (double) ok / n));
        }
        yield obj(
            "subjectId",
            c.q("subjectId"),
            "timeRange",
            c.q("timeRange"),
            "items",
            items,
            "algorithmVersion",
            "empirical-correct-rate-v1");
      }
      default -> throw new IllegalArgumentException(op);
    };
  }
}
