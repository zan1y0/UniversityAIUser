package com.kaoyan.ai.domain;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.RecordRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class QaService {
  private final RecordRepository db;
  private final CatalogService catalog;

  public QaService(RecordRepository db, CatalogService catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  public JsonNode execute(String op, DomainContext c) {
    return switch (op) {
      case "op037" -> {
        catalog.subject(c.b("subjectId"));
        ObjectNode conversation =
            obj(
                "id",
                id(),
                "subjectId",
                c.b("subjectId"),
                "contextVersion",
                1,
                "createdAt",
                now(),
                "updatedAt",
                now());
        db.create("conversations", conversation.path("id").asText(), c.user(), conversation);
        yield conversation;
      }
      case "op040" -> {
        db.get("conversations", c.path("id"), c.user());
        yield page(
            db.list("qa_messages", c.user()).stream()
                .filter(x -> x.path("conversationId").asText().equals(c.path("id")))
                .toList(),
            c.query());
      }
      case "op041" -> db.get("qa_messages", c.path("id"), c.user());
      case "op042" -> {
        ObjectNode m = db.get("qa_messages", c.path("id"), c.user());
        version(m, c.integer("version"));
        if (!Set.of("queued", "running").contains(m.path("status").asText()))
          throw ApiException.state("该消息已结束");
        int v = m.path("version").asInt();
        m.put("status", "cancelled");
        m.put("version", v + 1);
        db.update("qa_messages", c.path("id"), c.user(), m, v);
        for (ObjectNode job : db.list("jobs", c.user()))
          if (job.path("resourceId").asText().equals(c.path("id"))
              && Set.of("queued", "running").contains(job.path("status").asText())) {
            int jv = job.path("version").asInt();
            job.put("status", "cancelled");
            job.put("version", jv + 1);
            job.put("updatedAt", now());
            db.update("jobs", job.path("id").asText(), c.user(), job, jv);
          }
        yield m;
      }
      default -> throw new IllegalArgumentException(op);
    };
  }

  public ObjectNode prepareMessage(DomainContext c) {
    for (ObjectNode m : db.list("qa_messages", c.user()))
      if (m.path("conversationId").asText().equals(c.path("id"))
          && Set.of("queued", "running").contains(m.path("status").asText()))
        throw ApiException.state("请等待上一条回复完成或先停止回复");
    ObjectNode conversation = db.get("conversations", c.path("id"), c.user());
    require(conversation.path("subjectId").asText().equals(c.b("subjectId")), "追问科目不能改变");
    int cv = conversation.path("contextVersion").asInt();
    if (cv != c.integer("contextVersion"))
      throw new ApiException(409, "VERSION_CONFLICT", "会话上下文已更新，请刷新消息");
    String text = c.body().has("text") ? c.b("text") : c.b("confirmedText");
    if (c.body().has("recognitionId")) {
      ObjectNode recognition = db.get("recognitions", c.b("recognitionId"), c.user());
      require(recognition.path("subjectId").asText().equals(c.b("subjectId")), "识别记录科目不匹配");
    }
    int recordVersion =
        db.jdbc()
            .queryForObject(
                "SELECT version FROM conversations WHERE id=? AND owner_id=?",
                Integer.class,
                c.path("id"),
                c.user());
    conversation.put("contextVersion", cv + 1);
    conversation.put("updatedAt", now());
    db.update("conversations", c.path("id"), c.user(), conversation, recordVersion);
    ObjectNode input =
        obj(
            "id",
            id(),
            "conversationId",
            c.path("id"),
            "role",
            "user",
            "inputText",
            text,
            "status",
            "completed",
            "guidance",
            null,
            "contextVersion",
            cv + 1,
            "version",
            1,
            "createdAt",
            now());
    db.create("qa_messages", input.path("id").asText(), c.user(), input);
    ObjectNode output =
        obj(
            "id",
            id(),
            "conversationId",
            c.path("id"),
            "role",
            "assistant",
            "inputText",
            text,
            "status",
            "queued",
            "guidance",
            null,
            "contextVersion",
            cv + 1,
            "version",
            1,
            "createdAt",
            now());
    db.create("qa_messages", output.path("id").asText(), c.user(), output);
    return output;
  }

  public String context(DomainContext c) {
    StringBuilder text = new StringBuilder();
    for (ObjectNode m : db.list("qa_messages", c.user()))
      if (m.path("conversationId").asText().equals(c.path("id"))) {
        text.append(m.path("role").asText())
            .append(": ")
            .append(m.path("inputText").asText())
            .append('\n');
        if (!m.path("guidance").isNull()) text.append(m.path("guidance")).append('\n');
      }
    return text.substring(Math.max(0, text.length() - 24000));
  }
}
