package com.kaoyan.ai.service;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AttemptMetadata {
  private static final List<String> FIELDS =
      List.of("knowledgeId", "subjectId", "knowledgeVersion", "difficultyGrade");
  public static final int MAX_DURATION_MS = 24 * 60 * 60 * 1000;
  private final RecordRepository db;

  public AttemptMetadata(RecordRepository db) {
    this.db = db;
  }

  public static boolean hasAnswer(JsonNode answer) {
    return switch (answer.path("kind").asText()) {
      case "text" -> !answer.path("content").asText().isBlank();
      case "multipleChoice" -> answer.path("optionIds").isArray() && !answer.path("optionIds").isEmpty();
      case "singleChoice" -> !answer.path("optionId").asText().isBlank();
      case "numeric" -> !answer.path("value").asText().isBlank();
      default -> false;
    };
  }

  public static JsonNode duration(JsonNode value) {
    return value.isIntegralNumber() && value.canConvertToInt()
        && value.asInt() >= 0 && value.asInt() <= MAX_DURATION_MS ? value : null;
  }

  public void copyQuestionMetadata(ObjectNode target, JsonNode question) {
    for (String field : FIELDS) target.set(field, question.get(field));
    if (!target.hasNonNull("difficultyGrade"))
      target.set("difficultyGrade", CatalogService.difficultyGrade(question.path("difficulty").asText()));
  }

  public Snapshots snapshots(String user) {
    return new Snapshots(user);
  }

  public final class Snapshots {
    private final String user;
    private final Map<String, JsonNode> sessions = new HashMap<>();
    private final Map<String, JsonNode> versions = new HashMap<>();

    private Snapshots(String user) {
      this.user = user;
    }

    public JsonNode resolve(ObjectNode attempt) {
      if (attempt.hasNonNull("knowledgeId") && attempt.hasNonNull("subjectId")) return attempt;
      String version = attempt.path("questionVersionId").asText();
      JsonNode session = sessions.computeIfAbsent(attempt.path("sessionId").asText(),
          id -> db.find("practice_sessions", id, user).<JsonNode>map(n -> n).orElse(obj()));
      for (JsonNode question : session.path("questions"))
        if (question.path("versionId").asText().equals(version)) return question;
      return versions.computeIfAbsent(version, id -> db.find("questions", id, "public")
          .map(n -> n.path("question")).orElseGet(() -> obj()));
    }
  }
}
