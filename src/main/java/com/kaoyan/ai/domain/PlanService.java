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
public class PlanService {
  private final RecordRepository db;
  private final ProfileService profiles;
  private final CatalogService catalog;

  public PlanService(RecordRepository db, ProfileService profiles, CatalogService catalog) {
    this.db = db;
    this.profiles = profiles;
    this.catalog = catalog;
  }

  public ObjectNode get(String user, String id) {
    return db.get("plans", id, user);
  }

  public ObjectNode generate(DomainContext c, String replaceId) {
    JsonNode b = c.body();
    LocalDate target = LocalDate.parse(c.b("targetDate"));
    require(target.isAfter(today()) && target.isBefore(today().plusYears(3)), "目标日期必须晚于今天且在三年内");
    ObjectNode profile = profiles.get(c.user());
    version(profile, c.integer("profileVersion"));
    int budget = c.integer("dailyMinutes");
    List<String> subjects = new ArrayList<>();
    Map<String, Double> weights = new HashMap<>();
    for (JsonNode x : b.path("baseline")) {
      catalog.subject(x.path("subjectId").asText());
      require(x.path("score").asDouble() <= x.path("maxScore").asDouble(), "基础分数不得超过满分");
      String sid = x.path("subjectId").asText();
      subjects.add(sid);
      weights.put(sid, 1.0 + (1 - x.path("score").asDouble() / x.path("maxScore").asDouble()));
    }
    if (subjects.isEmpty()) {
      for (ObjectNode t : catalog.records("knowledge_trees"))
        if (t.has("category") && !subjects.contains(t.path("subjectId").asText())) {
          subjects.add(t.path("subjectId").asText());
          weights.put(t.path("subjectId").asText(), 1.0);
        }
    }
    require(!subjects.isEmpty(), "没有可用于学习规划的课程资料");
    ArrayNode tasks = arr();
    int maxDays = (int) Math.min(730, java.time.temporal.ChronoUnit.DAYS.between(today(), target));
    double weightSum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
    for (int d = 0; d < maxDays; d++) {
      int remaining = budget;
      for (int i = 0; i < subjects.size() && remaining > 0; i++) {
        String sid = subjects.get(i);
        int minutes =
            i == subjects.size() - 1
                ? remaining
                : Math.min(
                    remaining,
                    Math.max(1, (int) Math.floor(budget * weights.get(sid) / weightSum)));
        remaining -= minutes;
        JsonNode tree = catalog.subject(sid);
        JsonNode k =
            tree.path("nodes").isEmpty()
                ? null
                : tree.path("nodes").get(d % tree.path("nodes").size());
        tasks.add(
            obj(
                "id",
                id(),
                "subjectId",
                sid,
                "knowledgeId",
                k == null ? null : k.get("id"),
                "scheduledDate",
                today().plusDays(d).toString(),
                "minutes",
                minutes,
                "content",
                (d % 7 == 6 ? "周测与错题复盘：" : "知识点复习与练习：")
                    + (k == null ? "课程综合复习" : k.path("name").asText()),
                "status",
                "pending",
                "completedAt",
                null,
                "version",
                1));
      }
    }
    String pid = id();
    ObjectNode p =
        obj(
            "id",
            pid,
            "status",
            "draft",
            "targetDate",
            target.toString(),
            "dailyMinutes",
            budget,
            "profileVersion",
            profile.get("version"),
            "tasks",
            tasks,
            "view",
            "day",
            "viewDate",
            today().toString(),
            "version",
            1,
            "createdAt",
            now());
    db.create("plans", pid, c.user(), p);
    return obj(
        "kind",
        "plan",
        "plan",
        p,
        "reason",
        replaceId == null ? "按剩余时间、每日预算和基础薄弱程度分配；确认后才生效。" : "基于已完成任务生成调整草稿，确认前原计划继续生效。",
        "sources",
        arr(),
        "modelVersion",
        "rule-planner-v1");
  }

  public ObjectNode progress(String user, ObjectNode plan, String range) {
    int total = 0, complete = 0, count = 0, done = 0;
    LocalDate start =
        range.equals("week")
            ? today().minusDays(6)
            : range.equals("month") ? today().minusDays(29) : LocalDate.of(2000, 1, 1);
    for (JsonNode t : plan.path("tasks")) {
      LocalDate date = LocalDate.parse(t.path("scheduledDate").asText());
      if (!date.isBefore(start)) {
        total += t.path("minutes").asInt();
        count++;
        if (t.path("status").asText().equals("completed")) {
          complete += t.path("minutes").asInt();
          done++;
        }
      }
    }
    return obj(
        "planId",
        plan.get("id"),
        "timeRange",
        range,
        "plannedMinutes",
        total,
        "completedMinutes",
        complete,
        "plannedTasks",
        count,
        "completedTasks",
        done,
        "completionRate",
        count == 0 ? null : (double) done / count,
        "version",
        plan.get("version"));
  }

  public JsonNode execute(String op, DomainContext c) {
    String uid = c.user(), pid = c.path("id");
    ObjectNode p = get(uid, pid);
    int pv = p.path("version").asInt();
    return switch (op) {
      case "op051" -> {
        ObjectNode result = p.deepCopy();
        String view = c.q("view");
        LocalDate date = LocalDate.parse(c.q("date"));
        LocalDate from =
            view.equals("week")
                ? date.with(java.time.DayOfWeek.MONDAY)
                : view.equals("month") ? date.withDayOfMonth(1) : date;
        LocalDate to =
            view.equals("week")
                ? from.plusDays(6)
                : view.equals("month") ? from.plusMonths(1).minusDays(1) : date;
        ArrayNode tasks = arr();
        for (JsonNode t : p.path("tasks")) {
          LocalDate d = LocalDate.parse(t.path("scheduledDate").asText());
          if (!d.isBefore(from) && !d.isAfter(to)) tasks.add(t);
        }
        result.set("tasks", tasks);
        result.put("view", view);
        result.put("viewDate", date.toString());
        yield result;
      }
      case "op052", "op055" -> {
        ObjectNode task = null;
        for (JsonNode t : p.path("tasks"))
          if (t.path("id").asText().equals(c.path("taskId"))) task = (ObjectNode) t;
        if (task == null) throw ApiException.missing();
        version(task, c.integer("version"));
        int tv = task.path("version").asInt();
        if (op.equals("op052")) {
          if (!p.path("status").asText().equals("draft"))
            throw new ApiException(409, "PLAN_NOT_DRAFT", "仅草稿计划可以直接修改任务");
          for (String k : List.of("scheduledDate", "minutes", "content"))
            if (c.body().has(k)) task.set(k, c.body().get(k));
          require(
              !LocalDate.parse(task.path("scheduledDate").asText())
                  .isAfter(LocalDate.parse(p.path("targetDate").asText())),
              "任务日期不能晚于目标日期");
          int dayMinutes = 0;
          for (JsonNode t : p.path("tasks"))
            if (t.path("scheduledDate").asText().equals(task.path("scheduledDate").asText()))
              dayMinutes += t.path("minutes").asInt();
          require(dayMinutes <= 1440, "单日计划超过 24 小时");
        } else {
          if (!p.path("status").asText().equals("active")) throw ApiException.state("仅生效计划可完成任务");
          if (task.path("status").asText().equals("completed")) throw ApiException.state("任务已完成");
          task.put("status", "completed");
          task.put("completedAt", now());
        }
        task.put("version", tv + 1);
        p.put("version", pv + 1);
        db.update("plans", pid, uid, p, pv);
        yield task;
      }
      case "op053" -> {
        version(p, c.integer("version"));
        if (!p.path("status").asText().equals("draft"))
          throw new ApiException(409, "PLAN_NOT_DRAFT", "计划已确认或已被替换");
        ObjectNode profile = profiles.get(uid);
        int oldProfile = profile.path("version").asInt();
        version(profile, p.path("profileVersion").asInt());
        String active = profile.path("activePlanId").asText();
        if (!active.isEmpty() && !profile.path("activePlanId").isNull()) {
          ObjectNode previous = get(uid, active);
          int v = previous.path("version").asInt();
          previous.put("status", "superseded");
          previous.put("version", v + 1);
          db.update("plans", active, uid, previous, v);
        }
        p.put("status", "active");
        p.put("version", pv + 1);
        db.update("plans", pid, uid, p, pv);
        profile.put("activePlanId", pid);
        profile.put("version", oldProfile + 1);
        db.update("profiles", uid, uid, profile, oldProfile);
        yield p;
      }
      case "op056" -> progress(uid, p, c.q("timeRange"));
      default -> throw new IllegalArgumentException(op);
    };
  }

  public JsonNode adjustment(DomainContext c) {
    ObjectNode p = get(c.user(), c.path("id"));
    require(p.path("status").asText().equals("active"), "只能调整生效计划");
    version(p, c.integer("progressVersion"));
    ObjectNode profile = profiles.get(c.user());
    JsonNode input =
        obj(
            "profileVersion",
            profile.get("version"),
            "targetDate",
            p.get("targetDate"),
            "dailyMinutes",
            c.integer("timeBudget"),
            "baseline",
            profile.get("baseline"));
    return generate(new DomainContext(c.user(), c.paths(), c.query(), input), c.path("id"));
  }
}
