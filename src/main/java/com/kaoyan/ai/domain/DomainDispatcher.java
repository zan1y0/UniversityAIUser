package com.kaoyan.ai.domain;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.ai.ModelGateway;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.jobs.*;
import com.kaoyan.ai.persistence.RecordRepository;
import com.kaoyan.ai.storage.FileService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DomainDispatcher {
  private static final Set<String> ASYNC =
      Set.of(
          "op014", "op023", "op026", "op028", "op029", "op030", "op033", "op035", "op036", "op038",
          "op039", "op050", "op054", "op061", "op064", "op066", "op067", "op069");
  private final ProfileService profiles;
  private final CatalogService catalog;
  private final AvatarService avatar;
  private final PracticeService practice;
  private final PlanService plans;
  private final ExamService exams;
  private final QaService qa;
  private final AnalyticsService analytics;
  private final NewsService news;
  private final InterviewService interviews;
  private final RecordRepository db;
  private final JobService jobs;
  private final FileService files;
  private final ModelGateway model;

  public DomainDispatcher(
      ProfileService profiles,
      CatalogService catalog,
      AvatarService avatar,
      PracticeService practice,
      PlanService plans,
      ExamService exams,
      QaService qa,
      AnalyticsService analytics,
      NewsService news,
      InterviewService interviews,
      RecordRepository db,
      JobService jobs,
      FileService files,
      ModelGateway model) {
    this.profiles = profiles;
    this.catalog = catalog;
    this.avatar = avatar;
    this.practice = practice;
    this.plans = plans;
    this.exams = exams;
    this.qa = qa;
    this.analytics = analytics;
    this.news = news;
    this.interviews = interviews;
    this.db = db;
    this.jobs = jobs;
    this.files = files;
    this.model = model;
  }

  public JsonNode execute(String op, DomainContext c) {
    if (ASYNC.contains(op)) return enqueue(op, c);
    int n = Integer.parseInt(op.substring(2));
    if (n == 4) return profiles.get(c.user());
    if (n == 5) return profiles.update(c);
    if (n == 8) {
      files.delete(c.path("id"), c.user());
      return null;
    }
    if (n == 9) return jobs.get(c.user(), c.path("id"));
    if (n >= 11 && n <= 18) return avatar.execute(op, c);
    if (n >= 19 && n <= 36 || n == 57 || n == 70) {
      JsonNode out = catalog.execute(op, c);
      return n == 25 ? catalog.publicTree((ObjectNode) out) : out;
    }
    if (n >= 37 && n <= 42) return qa.execute(op, c);
    if (n >= 43 && n <= 49) return practice.execute(op, c);
    if (n >= 51 && n <= 56) return plans.execute(op, c);
    if (n >= 58 && n <= 64) return exams.execute(op, c);
    if (n >= 65 && n <= 69) return interviews.execute(op, c);
    if (n >= 71 && n <= 74) return analytics.execute(op, c);
    if (n >= 75 && n <= 81) return news.execute(op, c);
    throw new IllegalArgumentException(op);
  }

  private JsonNode enqueue(String op, DomainContext c) {
    String kind, resource = null;
    switch (op) {
      case "op014" -> {
        kind = "avatarInteraction";
        if (c.body().has("fileId")) files.metadata(c.b("fileId"), c.user(), "avatarAudio");
      }
      case "op023" -> {
        kind = "schoolEvaluation";
        catalog.program(c.b("programId"));
        version(profiles.get(c.user()), c.integer("profileVersion"));
      }
      case "op026" -> {
        kind = "explanation";
        catalog.knowledge(c.b("knowledgeId"));
        require(c.integer("version") == 1, "知识库版本不匹配");
      }
      case "op028", "op029", "op030" -> kind = "studyFeedback";
      case "op033" -> {
        kind = "explanation";
        ObjectNode book = db.publicGet("books", c.b("bookId"));
        require(book.path("edition").asText().equals(c.b("edition")), "参考书版本不匹配");
        boolean found = false;
        for (JsonNode x : book.path("chapters"))
          if (x.path("id").asText().equals(c.b("chapterId"))) found = true;
        require(found, "章节不属于当前参考书");
      }
      case "op035" -> {
        kind = "explanation";
        catalog.questionVersion(c.b("questionVersionId"));
      }
      case "op036" -> {
        kind = "prediction";
        catalog.subject(c.b("subjectId"));
        require(
            c.body().path("historyRange").path("fromYear").asInt()
                <= c.body().path("historyRange").path("toYear").asInt(),
            "历史年份范围错误");
      }
      case "op038" -> {
        kind = "recognition";
        files.metadata(c.b("fileId"), c.user(), "qaImage");
        catalog.subject(c.b("subjectId"));
      }
      case "op039" -> {
        kind = "qa";
        ObjectNode message = qa.prepareMessage(c);
        resource = message.path("id").asText();
        ObjectNode accepted = jobs.enqueue(op, kind, c, resource);
        accepted.remove("resourceId");
        accepted.put("messageId", resource);
        accepted.put("contextVersion", message.path("contextVersion").asInt());
        return accepted;
      }
      case "op050" -> {
        kind = "plan";
        version(profiles.get(c.user()), c.integer("profileVersion"));
        require(LocalDate.parse(c.b("targetDate")).isAfter(today()), "目标日期必须晚于今天");
      }
      case "op054" -> {
        kind = "plan";
        ObjectNode p = plans.get(c.user(), c.path("id"));
        version(p, c.integer("progressVersion"));
        require(p.path("status").asText().equals("active"), "仅生效计划可调整");
      }
      case "op061" -> {
        kind = "examScore";
        resource = c.path("id");
        exams.close(c.user(), resource, c.integer("version"));
        ObjectNode accepted = jobs.enqueue(op, kind, c, resource);
        ObjectNode submission =
            obj(
                "submissionId",
                id(),
                "sessionId",
                resource,
                "jobId",
                accepted.get("jobId"),
                "status",
                "queued",
                "scoringStatus",
                "queued",
                "submittedAt",
                now(),
                "serverTime",
                now(),
                "resultUrl",
                "/api/v1/mock-exams/sessions/" + resource + "/result");
        db.create("exam_submissions", resource, c.user(), submission);
        return submission;
      }
      case "op064" -> {
        kind = "scoreEstimate";
        version(profiles.get(c.user()), c.integer("profileVersion"));
        for (JsonNode s : c.body().path("scoreSnapshotIds"))
          db.get("score_snapshots", s.asText(), c.user());
      }
      case "op066" -> {
        kind = "interviewTurn";
        resource = c.path("id");
        interviews.ensureRunning(c);
        if (c.body().has("audioFileId"))
          files.metadata(c.b("audioFileId"), c.user(), "interviewAudio");
      }
      case "op067" -> {
        kind = "interviewReport";
        resource = c.path("id");
        interviews.finish(c);
      }
      case "op069" -> {
        kind = "introductionReview";
        catalog.program(c.b("programId"));
      }
      default -> throw new IllegalArgumentException(op);
    }
    return jobs.enqueue(op, kind, c, resource);
  }

  public boolean requiresModel(String op) {
    return Set.of(
            "op014", "op026", "op028", "op029", "op030", "op033", "op035", "op038", "op039",
            "op061", "op066", "op067", "op069")
        .contains(op);
  }

  public JsonNode persistModelResult(String op, DomainContext c, JsonNode result) {
    if (op.equals("op061")) return exams.saveResult(c.user(), c.path("id"), (ObjectNode) result);
    if (op.equals("op038")) db.create("recognitions", result.path("id").asText(), c.user(), result);
    if (op.equals("op039")) {
      ObjectNode m = (ObjectNode) result.path("message");
      ObjectNode current = db.get("qa_messages", m.path("id").asText(), c.user());
      if (current.path("status").asText().equals("cancelled")) throw ApiException.state("消息已取消");
      int v = current.path("version").asInt();
      m.put("version", v + 1);
      db.update("qa_messages", m.path("id").asText(), c.user(), m, v);
    }
    if (op.equals("op066")) {
      db.create("interview_turns", result.path("id").asText(), c.user(), result);
      ObjectNode i = db.get("interviews", c.path("id"), c.user());
      int v = i.path("version").asInt();
      i.put("version", v + 1);
      db.update("interviews", c.path("id"), c.user(), i, v);
    }
    if (op.equals("op067")) {
      ObjectNode old = db.get("interview_reports", c.path("id"), c.user());
      int v = old.path("version").asInt();
      ((ObjectNode) result).put("version", v + 1);
      db.update("interview_reports", c.path("id"), c.user(), (ObjectNode) result, v);
    }
    return result;
  }

  public JsonNode complete(String op, DomainContext c, JsonNode job) {
    return switch (op) {
      case "op050" -> plans.generate(c, null);
      case "op054" -> plans.adjustment(c);
      case "op061" -> exams.grade(c);
      case "op064" -> exams.estimate(c);
      case "op023" -> {
        ObjectNode p = catalog.program(c.b("programId"));
        String eid = id();
        ObjectNode evaluation =
            obj(
                "kind",
                "schoolEvaluation",
                "id",
                eid,
                "programId",
                c.b("programId"),
                "status",
                "insufficient_data",
                "probability",
                null,
                "reason",
                "缺少经过校准的历史录取样本，不能计算可靠上岸概率。",
                "missingFields",
                arr("calibratedHistoricalAdmissions"),
                "risks",
                arr("估计分数不能作为录取承诺"),
                "sources",
                p.get("sources"),
                "modelVersion",
                "data-availability-rule-v1",
                "calibrationVersion",
                null,
                "snapshotId",
                p.get("snapshotId"),
                "createdAt",
                now());
        db.create("evaluations", eid, c.user(), evaluation);
        yield evaluation;
      }
      case "op026", "op033", "op035" -> {
        String context =
            op.equals("op026")
                ? catalog.knowledge(c.b("knowledgeId")).toString()
                : op.equals("op035")
                    ? catalog.questionVersion(c.b("questionVersionId")).path("question").toString()
                    : db.publicGet("books", c.b("bookId")).toString();
        yield model.withSchema(
            "Explanation", "请按学习程度精讲，并仅给解题思路。输入：" + c.body() + "；课程资料：" + context);
      }
      case "op028", "op029", "op030" ->
          model.withSchema(
              "StudyFeedback", "公共课练习反馈；taskType 应为 " + c.b("type") + "。输入：" + c.body());
      case "op036" -> {
        JsonNode range = c.body().path("historyRange");
        int from = range.path("fromYear").asInt(), to = range.path("toYear").asInt();
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        for (ObjectNode x : catalog.records("questions")) {
          int year = x.path("year").asInt();
          if (year >= from
              && year <= to
              && x.path("question").path("subjectId").asText().equals(c.b("subjectId"))) {
            total++;
            counts.merge(x.path("question").path("knowledgeId").asText(), 1, Integer::sum);
          }
        }
        ArrayNode points = arr();
        for (var e : counts.entrySet())
          points.add(
              obj(
                  "knowledgeId",
                  e.getKey(),
                  "reason",
                  "在当前题库历史范围出现 " + e.getValue() + " 次；仅供复习优先级参考。",
                  "frequency",
                  (double) e.getValue() / total,
                  "confidence",
                  0));
        yield obj(
            "kind",
            "prediction",
            "subjectId",
            c.b("subjectId"),
            "targetYear",
            c.integer("targetYear"),
            "points",
            points,
            "disclaimer",
            "题库频率不代表命题概率；未校准置信度置零，不保证押题命中。",
            "sources",
            arr(),
            "modelVersion",
            "historical-frequency-v1");
      }
      case "op038" -> {
        ObjectNode file = files.metadata(c.b("fileId"), c.user(), "qaImage");
        String rid = id();
        JsonNode result =
            model.recognize(
                "Recognition",
                "仅识别文字，不解题；id="
                    + rid
                    + "，fileId="
                    + c.b("fileId")
                    + "，subjectId="
                    + c.b("subjectId")
                    + "，requiresConfirmation=true，createdAt="
                    + now(),
                file.path("mimeType").asText(),
                files.bytes(c.b("fileId"), c.user(), "qaImage"));
        ObjectNode n = (ObjectNode) result;
        n.put("id", rid);
        n.put("fileId", c.b("fileId"));
        n.put("subjectId", c.b("subjectId"));
        n.put("requiresConfirmation", true);
        n.put("createdAt", now());
        yield n;
      }
      case "op039" -> {
        ObjectNode m = db.get("qa_messages", job.path("resourceId").asText(), c.user());
        JsonNode guidance =
            model.withSchema(
                "QaGuidance", "多轮学习问题：" + qa.context(c) + "。只能给步骤、提示、方法，绝不能给最终答案。避免使用答案结论字段。");
        m.set("guidance", guidance);
        m.put("status", "completed");
        yield obj("kind", "qa", "message", m);
      }
      case "op014" -> {
        String text = c.b("text");
        String transcript = null;
        if (c.body().has("fileId")) {
          ObjectNode f = files.metadata(c.b("fileId"), c.user(), "avatarAudio");
          text =
              model.transcribe(
                  files.bytes(c.b("fileId"), c.user(), "avatarAudio"),
                  f.path("mimeType").asText(),
                  "audio.wav");
          transcript = text;
        }
        JsonNode response =
            model.withSchema(
                "AvatarInteraction",
                "数字人陪伴督学，参考用户模式："
                    + avatar.execute("op012", c)
                    + "；用户说："
                    + text
                    + "；audioFileId 返回 null；不要虚构生成音频。");
        ObjectNode result = (ObjectNode) response;
        result.set("transcript", MAPPER.valueToTree(transcript));
        result.putNull("audioFileId");
        yield result;
      }
      case "op066" -> {
        ObjectNode interview = db.get("interviews", c.path("id"), c.user());
        String text = c.b("text");
        if (c.body().has("audioFileId")) {
          ObjectNode f = files.metadata(c.b("audioFileId"), c.user(), "interviewAudio");
          text =
              model.transcribe(
                  files.bytes(c.b("audioFileId"), c.user(), "interviewAudio"),
                  f.path("mimeType").asText(),
                  "audio.wav");
        }
        String tid = id();
        ObjectNode result =
            (ObjectNode)
                model.withSchema(
                    "InterviewTurn",
                    "模拟复试；id="
                        + tid
                        + "，interviewId="
                        + c.path("id")
                        + "，transcript="
                        + text
                        + "。根据表现提供评分和追问，不能臆测真实招生安排。会话："
                        + interview);
        result.put("id", tid);
        result.put("interviewId", c.path("id"));
        result.put("transcript", text);
        yield result;
      }
      case "op067" -> {
        var turns =
            db.list("interview_turns", c.user()).stream()
                .filter(x -> x.path("interviewId").asText().equals(c.path("id")))
                .toList();
        ObjectNode result =
            (ObjectNode)
                model.withSchema(
                    "InterviewReport",
                    "仅基于完成的复试对话评分；interviewId="
                        + c.path("id")
                        + "，status=completed，version=2，rubricVersion=interview-v1；turns="
                        + turns);
        result.put("interviewId", c.path("id"));
        result.set("turns", MAPPER.valueToTree(turns));
        ObjectNode old = db.get("interview_reports", c.path("id"), c.user());
        result.put("version", old.path("version").asInt() + 1);
        yield result;
      }
      case "op069" ->
          model.withSchema(
              "IntroductionReview", "批改自我介绍，original 及 language 必须与输入一致，不编造经历：" + c.body());
      default -> throw new IllegalArgumentException(op);
    };
  }
}
