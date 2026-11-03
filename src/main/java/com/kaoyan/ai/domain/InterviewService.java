package com.kaoyan.ai.domain;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.RecordRepository;
import org.springframework.stereotype.Service;

@Service
public class InterviewService {
  private final RecordRepository db;
  private final CatalogService catalog;

  public InterviewService(RecordRepository db, CatalogService catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  public JsonNode execute(String op, DomainContext c) {
    return switch (op) {
      case "op065" -> {
        catalog.program(c.b("programId"));
        ObjectNode question =
            obj(
                "id",
                id(),
                "text",
                c.b("mode").equals("english")
                    ? "Please introduce yourself and explain your research interests."
                    : c.b("mode").equals("pressure")
                        ? "如果你的研究方案被否定，你会如何定位问题并改进？"
                        : "请介绍你的专业背景，并说明一个你最熟悉的项目。",
                "audioFileId",
                null);
        ObjectNode interview =
            obj(
                "id",
                id(),
                "programId",
                c.b("programId"),
                "mode",
                c.b("mode"),
                "language",
                c.b("language"),
                "duration",
                c.integer("duration"),
                "status",
                "in_progress",
                "firstQuestion",
                question,
                "version",
                1,
                "createdAt",
                now());
        db.create("interviews", interview.path("id").asText(), c.user(), interview);
        yield interview;
      }
      case "op068" -> db.get("interview_reports", c.path("id"), c.user());
      default -> throw new IllegalArgumentException(op);
    };
  }

  public void ensureRunning(DomainContext c) {
    ObjectNode i = db.get("interviews", c.path("id"), c.user());
    if (!i.path("status").asText().equals("in_progress")) throw ApiException.state("复试训练已结束");
    require(
        java.time.Instant.parse(i.path("createdAt").asText())
            .plusSeconds(i.path("duration").asInt())
            .isAfter(java.time.Instant.now()),
        "复试训练时间已结束");
  }

  public void finish(DomainContext c) {
    ObjectNode i = db.get("interviews", c.path("id"), c.user());
    version(i, c.integer("version"));
    if (!i.path("status").asText().equals("in_progress")) throw ApiException.state("复试训练已结束");
    int v = i.path("version").asInt();
    i.put("status", "finished");
    i.put("version", v + 1);
    db.update("interviews", c.path("id"), c.user(), i, v);
    ObjectNode report =
        obj(
            "kind",
            "interviewReport",
            "interviewId",
            c.path("id"),
            "status",
            "queued",
            "turns",
            arr(),
            "dimensions",
            arr(),
            "summary",
            null,
            "englishSuggestions",
            arr(),
            "rubricVersion",
            "interview-v1",
            "version",
            1);
    db.create("interview_reports", c.path("id"), c.user(), report);
  }
}
