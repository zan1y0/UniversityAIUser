package com.kaoyan.ai.service;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TrainingSequenceService {
  private final RecordRepository db;
  private final AttemptMetadata metadata;

  public TrainingSequenceService(RecordRepository db, AttemptMetadata metadata) {
    this.db = db;
    this.metadata = metadata;
  }

  public ObjectNode export(DomainContext c) {
    Instant now = Instant.now();
    Instant cutoff = c.query().hasNonNull("asOf") ? instant(c.q("asOf")) : now;
    require(cutoff != null && !cutoff.isAfter(now), "导出截止时间必须是有效的历史 UTC 时间");
    ObjectNode page = page(sequence(c.user(), c.q("subjectId"), cutoff), c.query());
    page.put("asOf", cutoff.toString());
    page.put("schemaVersion", "training-sequence-v1");
    return page;
  }

  public List<ObjectNode> sequence(String user, String subject, Instant cutoff) {
    List<ObjectNode> rows = new ArrayList<>();
    var snapshots = metadata.snapshots(user);
    String studentKey = db.fingerprint(obj("trainingStudent", user));
    Map<String, Integer> legacySequences = new HashMap<>();
    Map<List<String>, Integer> legacyAttempts = new HashMap<>();
    List<ObjectNode> attempts = new ArrayList<>(db.list("attempts", user));
    attempts.sort(Comparator.comparing((ObjectNode a) -> instant(a.path("createdAt").asText()),
        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(a -> a.path("id").asText()));
    for (ObjectNode attempt : attempts) {
      Instant scoredAt = instant(attempt.path("createdAt").asText());
      if (scoredAt == null || scoredAt.isAfter(cutoff)) continue;
      String session = attempt.path("sessionId").asText();
      String questionVersion = attempt.path("questionVersionId").asText();
      JsonNode snapshot = snapshots.resolve(attempt);
      int seq = legacySequences.merge(session, 1, Integer::sum);
      int attemptNo = legacyAttempts.merge(List.of(session, questionVersion), 1, Integer::sum);
      if (!matchesSubject(snapshot, subject)) continue;
      Instant submittedAt = instant(attempt.path("submittedAt").asText());
      ObjectNode row = row(attempt, snapshot, studentKey, "practice", session, questionVersion,
          attempt.path("id").asText(), attempt.path("seq").asInt(seq),
          submittedAt == null ? scoredAt : submittedAt, scoredAt, "answered");
      row.put("attemptNo", attempt.path("attemptNo").asInt(attemptNo));
      row.set("changed", attempt.get("changed"));
      rows.add(row);
    }
    Map<String, ObjectNode> results = new HashMap<>();
    for (ObjectNode result : db.list("exam_results", user))
      if ("completed".equals(result.path("scoringStatus").asText()))
        results.put(result.path("sessionId").asText(), result);
    for (ObjectNode session : db.list("exam_sessions", user)) {
      if (!"submitted".equals(session.path("status").asText())) continue;
      String sessionId = session.path("id").asText();
      JsonNode result = results.get(sessionId);
      if (result == null) continue;
      Instant scoredAt = instant(result.path("scoredAt").asText());
      Instant submittedAt = instant(session.path("submittedAt").asText());
      if (scoredAt == null || scoredAt.isAfter(cutoff) || submittedAt == null) continue;
      int seq = 0;
      for (JsonNode answer : session.path("answers")) {
        seq++;
        if (!answer.hasNonNull("questionVersionId") || !matchesSubject(answer, subject)) continue;
        String questionVersion = answer.path("questionVersionId").asText();
        Instant savedAt = instant(answer.path("savedAt").asText());
        rows.add(row(answer, answer, studentKey, "exam", sessionId, questionVersion,
            sessionId + "/" + questionVersion, seq, savedAt == null ? submittedAt : savedAt,
            scoredAt, answer.path("status").asText()));
      }
    }
    rows.sort(Comparator.comparing((ObjectNode a) -> instant(a.path("createdAt").asText()))
        .thenComparing(a -> a.path("sessionId").asText())
        .thenComparingInt(a -> a.path("seq").asInt())
        .thenComparing(a -> a.path("eventId").asText()));
    return rows;
  }

  private ObjectNode row(JsonNode attempt, JsonNode snapshot, String studentKey, String source,
      String session, String question, String eventId, int seq, Instant submittedAt,
      Instant scoredAt, String status) {
    ArrayNode flags = arr();
    JsonNode duration = AttemptMetadata.duration(attempt.path("durationMs"));
    if (duration == null && attempt.hasNonNull("durationMs")) flags.add("invalid_duration");
    if (!snapshot.hasNonNull("knowledgeId") || !snapshot.hasNonNull("subjectId"))
      flags.add("missing_question_metadata");
    boolean answered = status.equals("answered");
    boolean hasAnswer = AttemptMetadata.hasAnswer(attempt.path("answer"));
    if (answered && !hasAnswer) flags.add("empty_answer");
    double score = attempt.path("score").asDouble(Double.NaN);
    double maxScore = attempt.path("maxScore").asDouble(Double.NaN);
    boolean validScore = attempt.path("score").isNumber() && attempt.path("maxScore").isNumber()
        && Double.isFinite(score) && Double.isFinite(maxScore)
        && maxScore >= 0 && score >= 0 && score <= maxScore;
    if (!validScore) flags.add("invalid_score");
    boolean usable = answered && hasAnswer && validScore && snapshot.hasNonNull("knowledgeId")
        && snapshot.hasNonNull("subjectId") && attempt.path("correct").isBoolean();
    ObjectNode row = obj("userId", studentKey, "eventId", eventId, "source", source,
        "sessionId", session, "seq", seq, "questionVersionId", question, "status", status,
        "correct", usable ? attempt.get("correct") : null, "score", validScore ? score : null,
        "maxScore", validScore ? maxScore : null,
        "scoreRatio", usable && maxScore > 0 ? score / maxScore : null,
        "createdAt", submittedAt.toString(), "scoredAt", scoredAt.toString(),
        "durationMs", duration, "qualityFlags", flags);
    metadata.copyQuestionMetadata(row, snapshot);
    return row;
  }

  private boolean matchesSubject(JsonNode snapshot, String subject) {
    return subject.isEmpty() || subject.equals(snapshot.path("subjectId").asText());
  }

  private static Instant instant(String value) {
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
