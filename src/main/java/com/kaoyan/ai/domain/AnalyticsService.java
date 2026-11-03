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
public class AnalyticsService {
  private final RecordRepository db;
  private final CatalogService catalog;

  public AnalyticsService(RecordRepository db, CatalogService catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  private List<ObjectNode> events(String user) {
    return db.list("study_events", user);
  }

  private List<long[]> intervals(String user, Instant from, Instant to) {
    List<long[]> in = new ArrayList<>();
    for (ObjectNode s : events(user)) {
      JsonNode e = s.path("event");
      long
          a =
              Math.max(
                  from.toEpochMilli(), Instant.parse(e.path("startAt").asText()).toEpochMilli()),
          b = Math.min(to.toEpochMilli(), Instant.parse(e.path("endAt").asText()).toEpochMilli());
      if (a < b) in.add(new long[] {a, b});
    }
    in.sort(Comparator.comparingLong(x -> x[0]));
    List<long[]> merged = new ArrayList<>();
    for (long[] item : in) {
      if (merged.isEmpty() || item[0] > merged.get(merged.size() - 1)[1]) merged.add(item.clone());
      else merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], item[1]);
    }
    return merged;
  }

  private double minutes(String user, LocalDate from, LocalDate to) {
    return intervals(
            user,
            from.atStartOfDay(ZONE).toInstant(),
            to.plusDays(1).atStartOfDay(ZONE).toInstant())
        .stream()
        .mapToDouble(x -> (x[1] - x[0]) / 60000.0)
        .sum();
  }

  private List<ObjectNode> attempts(String user, LocalDate from, LocalDate to, String subject) {
    return db.list("attempts", user).stream()
        .filter(
            a -> {
              LocalDate d = Instant.parse(a.path("createdAt").asText()).atZone(ZONE).toLocalDate();
              return !d.isBefore(from)
                  && !d.isAfter(to)
                  && (subject == null
                      || subject.isEmpty()
                      || catalog
                          .questionVersion(a.path("questionVersionId").asText())
                          .path("question")
                          .path("subjectId")
                          .asText()
                          .equals(subject));
            })
        .toList();
  }

  private Double rate(List<ObjectNode> list) {
    return list.isEmpty()
        ? null
        : (double) list.stream().filter(x -> x.path("correct").asBoolean()).count() / list.size();
  }

  public JsonNode execute(String op, DomainContext c) {
    String user = c.user();
    return switch (op) {
      case "op071" -> {
        JsonNode e = c.body();
        Instant start = Instant.parse(c.b("startAt")), end = Instant.parse(c.b("endAt"));
        require(
            end.isAfter(start)
                && !end.isAfter(Instant.now().plusSeconds(60))
                && Duration.between(start, end).toSeconds() <= 86400,
            "学习事件须为已发生的正时长，单次不超过24小时");
        if (!e.path("taskId").isNull()) {
          boolean found = false;
          for (ObjectNode p : db.list("plans", user))
            for (JsonNode t : p.path("tasks"))
              if (t.path("id").asText().equals(c.b("taskId"))) found = true;
          require(found, "任务不属于当前用户");
        }
        String key = c.b("eventId");
        var existing = db.find("study_events", key, user);
        if (existing.isPresent()) {
          if (!existing.get().path("event").equals(e))
            throw new ApiException(409, "IDEMPOTENCY_MISMATCH", "学习事件内容已变化");
          yield existing.get();
        }
        long overlap = 0;
        for (long[] x : intervals(user, start, end)) overlap += x[1] - x[0];
        ObjectNode saved =
            obj(
                "id",
                id(),
                "event",
                e,
                "effectiveMinutes",
                Math.max(0, (Duration.between(start, end).toMillis() - overlap) / 60000.0),
                "createdAt",
                now());
        db.create("study_events", key, user, saved);
        yield saved;
      }
      case "op072" -> {
        LocalDate from = LocalDate.parse(c.q("from")), to = LocalDate.parse(c.q("to"));
        validateRange(from, to);
        String subject = c.query().has("subjectId") ? c.q("subjectId") : null;
        if (subject != null) catalog.subject(subject);
        var all = attempts(user, from, to, subject);
        ArrayNode curve = arr();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
          var one = attempts(user, d, d, subject);
          curve.add(
              obj(
                  "date",
                  d.toString(),
                  "studyMinutes",
                  minutes(user, d, d),
                  "correctRate",
                  rate(one),
                  "attemptCount",
                  one.size()));
        }
        double numerator = 0, denominator = 0;
        for (ObjectNode s : events(user)) {
          JsonNode e = s.path("event");
          if (!e.path("focusSelfReport").isNull()) {
            Instant lo = from.atStartOfDay(ZONE).toInstant(),
                hi = to.plusDays(1).atStartOfDay(ZONE).toInstant();
            long duration =
                Math.max(
                    0,
                    Math.min(
                            hi.toEpochMilli(),
                            Instant.parse(e.path("endAt").asText()).toEpochMilli())
                        - Math.max(
                            lo.toEpochMilli(),
                            Instant.parse(e.path("startAt").asText()).toEpochMilli()));
            numerator += duration * e.path("focusSelfReport").asDouble();
            denominator += duration;
          }
        }
        yield obj(
            "from",
            from.toString(),
            "to",
            to.toString(),
            "timezone",
            "Asia/Shanghai",
            "studyMinutes",
            minutes(user, from, to),
            "focus",
            denominator == 0 ? null : numerator / denominator,
            "correctRate",
            rate(all),
            "attemptCount",
            all.size(),
            "curve",
            curve,
            "algorithmVersion",
            "interval-union-self-report-v1");
      }
      case "op073" -> {
        LocalDate from = LocalDate.parse(c.q("from")), to = LocalDate.parse(c.q("to"));
        validateRange(from, to);
        ArrayNode items = arr();
        var list = attempts(user, from, to, c.q("subjectId"));
        for (JsonNode k : catalog.subject(c.q("subjectId")).path("nodes")) {
          var selected =
              list.stream()
                  .filter(
                      a ->
                          catalog
                              .questionVersion(a.path("questionVersionId").asText())
                              .path("question")
                              .path("knowledgeId")
                              .asText()
                              .equals(k.path("id").asText()))
                  .toList();
          items.add(
              obj(
                  "knowledgeId",
                  k.get("id"),
                  "name",
                  k.get("name"),
                  "mastery",
                  rate(selected),
                  "sampleCount",
                  selected.size()));
        }
        yield obj(
            "subjectId",
            c.q("subjectId"),
            "from",
            from.toString(),
            "to",
            to.toString(),
            "items",
            items,
            "algorithmVersion",
            "empirical-correct-rate-v1");
      }
      case "op074" -> {
        LocalDate start = LocalDate.parse(c.q("weekStart"));
        require(start.getDayOfWeek() == DayOfWeek.MONDAY, "weekStart 必须是周一");
        double min = minutes(user, start, start.plusDays(6));
        var all = attempts(user, start, start.plusDays(6), null);
        yield obj(
            "weekStart",
            start.toString(),
            "timezone",
            "Asia/Shanghai",
            "status",
            "completed",
            "studyMinutes",
            min,
            "correctRate",
            rate(all),
            "summary",
            "本周累计学习 " + Math.round(min) + " 分钟，提交 " + all.size() + " 次练习。",
            "suggestions",
            all.isEmpty() ? arr("完成课程练习后再观察正确率与薄弱点。") : arr("复习错题，并在下周进行同类题训练。"),
            "algorithmVersion",
            "weekly-aggregate-v1",
            "generatedAt",
            now());
      }
      default -> throw new IllegalArgumentException(op);
    };
  }

  private void validateRange(LocalDate from, LocalDate to) {
    require(
        !to.isBefore(from) && java.time.temporal.ChronoUnit.DAYS.between(from, to) <= 365,
        "查询日期范围须为1至366天");
  }
}
