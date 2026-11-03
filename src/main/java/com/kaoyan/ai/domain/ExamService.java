package com.kaoyan.ai.domain;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.RecordRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ExamService {
  private final RecordRepository db;
  private final CatalogService catalog;
  private final PracticeService practice;
  private final com.kaoyan.ai.ai.ModelGateway model;

  public ExamService(
      RecordRepository db,
      CatalogService catalog,
      PracticeService practice,
      com.kaoyan.ai.ai.ModelGateway model) {
    this.db = db;
    this.catalog = catalog;
    this.practice = practice;
    this.model = model;
  }

  public ObjectNode session(String user, String id) {
    ObjectNode n = db.get("exam_sessions", id, user);
    n.put("serverTime", now());
    return n;
  }

  public ObjectNode paper(String version) {
    return db.publicGet("papers", version);
  }

  public JsonNode execute(String op, DomainContext c) {
    String uid = c.user();
    return switch (op) {
      case "op058" -> {
        ObjectNode stored = paper(c.b("paperVersionId"));
        JsonNode paper = stored.get("paper");
        for (ObjectNode active : db.list("exam_sessions", uid))
          if (active.path("status").asText().equals("in_progress")
              && active.path("paperVersionId").asText().equals(c.b("paperVersionId"))
              && Instant.parse(active.path("deadlineAt").asText()).isAfter(Instant.now()))
            throw ApiException.state("此试卷已有进行中的模考，请恢复该会话");
        ArrayNode qs = arr();
        for (JsonNode v : stored.path("questionVersionIds"))
          qs.add(catalog.displayQuestion(catalog.questionVersion(v.asText())));
        ObjectNode s =
            obj(
                "id",
                id(),
                "paperVersionId",
                c.b("paperVersionId"),
                "status",
                "in_progress",
                "startedAt",
                now(),
                "deadlineAt",
                Instant.now().plusSeconds(paper.path("durationMinutes").asLong() * 60).toString(),
                "serverTime",
                now(),
                "questions",
                qs,
                "answers",
                arr(),
                "version",
                1);
        db.create("exam_sessions", s.path("id").asText(), uid, s);
        yield s;
      }
      case "op059" -> session(uid, c.path("id"));
      case "op060" -> {
        ObjectNode s = session(uid, c.path("id"));
        ensureOpen(s);
        ObjectNode q = null;
        for (JsonNode x : s.path("questions"))
          if (x.path("id").asText().equals(c.path("qid"))) q = (ObjectNode) x;
        if (q == null) throw ApiException.missing();
        practice.validateAnswer(q, c.body().path("answer"));
        ArrayNode answers = (ArrayNode) s.get("answers");
        ObjectNode old = null;
        for (JsonNode a : answers)
          if (a.path("questionId").asText().equals(c.path("qid"))) old = (ObjectNode) a;
        int av = old == null ? 0 : old.path("version").asInt();
        if (av != c.integer("version"))
          throw new ApiException(409, "VERSION_CONFLICT", "本题答案已更新，请恢复会话");
        ObjectNode saved =
            obj(
                "questionId",
                c.path("qid"),
                "answer",
                c.body().get("answer"),
                "version",
                av + 1,
                "savedAt",
                now());
        if (old == null) answers.add(saved);
        else {
          old.removeAll();
          old.setAll(saved);
        }
        int sv = s.path("version").asInt();
        s.put("version", sv + 1);
        db.update("exam_sessions", c.path("id"), uid, s, sv);
        yield saved;
      }
      case "op062" -> db.get("exam_results", c.path("id"), uid);
      case "op063" -> {
        ObjectNode stored = paper(c.q("paperVersionId"));
        require(stored.path("paper").path("id").asText().equals(c.path("id")), "试卷与版本不匹配");
        var rows =
            db.jdbc()
                .query(
                    "SELECT owner_id,payload FROM exam_results ORDER BY created_at",
                    (rs, n) -> obj("owner", rs.getString(1), "result", parse(rs.getString(2))));
        List<ObjectNode> results =
            new ArrayList<>(
                rows.stream()
                    .filter(
                        x ->
                            x.path("result")
                                    .path("paperVersionId")
                                    .asText()
                                    .equals(c.q("paperVersionId"))
                                && x.path("result")
                                    .path("scoringStatus")
                                    .asText()
                                    .equals("completed"))
                    .toList());
        results.sort(
            Comparator.comparingDouble(x -> -x.path("result").path("totalScore").asDouble()));
        Set<String> seen = new HashSet<>();
        List<JsonNode> entries = new ArrayList<>();
        Integer myRank = null;
        double previous = Double.NaN;
        int rank = 0;
        for (ObjectNode row : results) {
          String owner = row.path("owner").asText();
          if (!seen.add(owner)) continue;
          double score = row.path("result").path("totalScore").asDouble();
          if (Double.compare(previous, score) != 0) rank = entries.size() + 1;
          previous = score;
          if (owner.equals(uid)) myRank = rank;
          String sid = row.path("result").path("sessionId").asText();
          String date = db.get("exam_sessions", sid, owner).path("startedAt").asText();
          entries.add(
              obj(
                  "rank",
                  rank,
                  "anonymousName",
                  "同学 " + owner.substring(0, 6),
                  "score",
                  score,
                  "submittedAt",
                  date));
        }
        ObjectNode out = page(entries, c.query());
        out.put("paperVersionId", c.q("paperVersionId"));
        out.put("sampleCount", entries.size());
        out.set("myRank", MAPPER.valueToTree(myRank));
        yield out;
      }
      default -> throw new IllegalArgumentException(op);
    };
  }

  private void ensureOpen(ObjectNode s) {
    if (!s.path("status").asText().equals("in_progress")
        || !Instant.parse(s.path("deadlineAt").asText()).isAfter(Instant.now()))
      throw new ApiException(409, "EXAM_CLOSED", "模考已提交或作答时间已结束");
  }

  public void close(String uid, String sid, int version) {
    ObjectNode s = session(uid, sid);
    version(s, version);
    if (!s.path("status").asText().equals("in_progress"))
      throw new ApiException(409, "EXAM_CLOSED", "模考已提交");
    s.put("status", "submitted");
    s.put("version", version + 1);
    db.update("exam_sessions", sid, uid, s, version);
    ObjectNode paper = paper(s.path("paperVersionId").asText());
    ObjectNode result =
        obj(
            "kind",
            "examScore",
            "sessionId",
            sid,
            "paperVersionId",
            s.get("paperVersionId"),
            "scoringStatus",
            "queued",
            "objectiveScore",
            null,
            "subjectiveEstimate",
            null,
            "totalScore",
            null,
            "maxScore",
            paper.path("paper").get("maxScore"),
            "dimensions",
            arr(),
            "weakSubjectIds",
            arr(),
            "rubricVersion",
            "objective-exact-v1",
            "modelVersion",
            null,
            "scoreSnapshotId",
            null,
            "version",
            1);
    db.create("exam_results", sid, uid, result);
  }

  public ObjectNode grade(DomainContext c) {
    ObjectNode session = session(c.user(), c.path("id"));
    ObjectNode result = db.get("exam_results", c.path("id"), c.user());
    double objective = 0, subjective = 0;
    ArrayNode dimensions = arr(), weak = arr();
    ApiException failure = null;
    boolean usedModel = false;
    for (JsonNode q : session.path("questions")) {
      JsonNode answer = null;
      for (JsonNode a : session.path("answers"))
        if (a.path("questionId").asText().equals(q.path("id").asText())) answer = a.get("answer");
      double points = 0;
      JsonNode dimension;
      if (q.path("kind").asText().equals("text")) {
        if (answer != null) {
          try {
            dimension =
                model.withSchema(
                    "ScoreDimension",
                    "按参考答案和评分标准评估本题。name=主观题，maxScore="
                        + q.path("maxScore").asDouble()
                        + "；题目："
                        + q
                        + "；学生作答："
                        + answer
                        + "；参考："
                        + catalog
                            .questionVersion(q.path("versionId").asText())
                            .path("correctAnswer"));
            require(
                dimension.path("score").asDouble() <= q.path("maxScore").asDouble()
                    && dimension.path("maxScore").asDouble() == q.path("maxScore").asDouble(),
                "模型评分超出本题范围");
            points = dimension.path("score").asDouble();
            usedModel = true;
          } catch (ApiException e) {
            failure = e;
            continue;
          }
        } else
          dimension =
              obj("name", "主观题", "score", 0, "maxScore", q.get("maxScore"), "feedback", "未作答");
        subjective += points;
      } else {
        boolean correct =
            answer != null
                && practice.correct(
                    catalog.questionVersion(q.path("versionId").asText()).get("correctAnswer"),
                    answer);
        points = correct ? q.path("maxScore").asDouble() : 0;
        objective += points;
        dimension =
            obj(
                "name",
                "客观题 " + (dimensions.size() + 1),
                "score",
                points,
                "maxScore",
                q.get("maxScore"),
                "feedback",
                correct ? "作答正确" : "复习对应知识点");
      }
      dimensions.add(dimension);
      if (points < q.path("maxScore").asDouble()
          && !weak.toString().contains(q.path("subjectId").asText())) weak.add(q.get("subjectId"));
    }
    result.put("objectiveScore", objective);
    result.set("dimensions", dimensions);
    result.set("weakSubjectIds", weak);
    result.set("modelVersion", MAPPER.valueToTree(usedModel ? model.version() : null));
    if (failure != null) {
      result.put("scoringStatus", "failed_retryable");
      result.putNull("subjectiveEstimate");
      result.putNull("totalScore");
      throw new PartialScoringException(result, failure);
    }
    result.put("subjectiveEstimate", subjective);
    result.put("totalScore", objective + subjective);
    result.put("scoringStatus", "completed");
    result.put("scoreSnapshotId", id());
    return result;
  }

  public ObjectNode saveResult(String user, String sessionId, ObjectNode result) {
    ObjectNode old = db.get("exam_results", sessionId, user);
    int version = old.path("version").asInt();
    result.put("version", version + 1);
    db.update("exam_results", sessionId, user, result, version);
    if (result.path("scoringStatus").asText().equals("completed")) {
      String snapshot = result.path("scoreSnapshotId").asText();
      db.create(
          "score_snapshots",
          snapshot,
          user,
          obj(
              "id",
              snapshot,
              "sessionId",
              sessionId,
              "score",
              result.get("totalScore"),
              "maxScore",
              result.get("maxScore"),
              "createdAt",
              now()));
    }
    return result;
  }

  public ObjectNode estimate(DomainContext c) {
    double sum = 0;
    Double scale = null;
    List<Double> scores = new ArrayList<>();
    for (JsonNode id : c.body().path("scoreSnapshotIds")) {
      ObjectNode s = db.get("score_snapshots", id.asText(), c.user());
      double value = s.path("score").asDouble();
      if (scale == null) scale = s.path("maxScore").asDouble();
      else require(scale == s.path("maxScore").asDouble(), "估分快照必须使用相同满分量尺");
      scores.add(value);
      sum += value;
    }
    double mean = sum / scores.size();
    double variance = 0;
    for (double score : scores) variance += Math.pow(score - mean, 2);
    double spread = scores.size() > 1 ? Math.sqrt(variance / (scores.size() - 1)) : 0;
    return obj(
        "kind",
        "scoreEstimate",
        "estimatedScore",
        mean,
        "lowerBound",
        Math.max(0, mean - spread),
        "upperBound",
        Math.min(500, mean + spread),
        "strategy",
        arr("估分仅基于提交的同量尺成绩快照；样本少时不能解释为统计置信区间。", "优先复习模考错题及薄弱知识点。"),
        "scoreSnapshotIds",
        c.body().get("scoreSnapshotIds"),
        "methodVersion",
        "snapshot-mean-stddev-v1",
        "sources",
        arr(),
        "modelVersion",
        "rule-estimator-v1");
  }
}
