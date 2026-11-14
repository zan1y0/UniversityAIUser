package com.kaoyan.ai.service.task;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.kaoyan.ai.service.AvatarService;
import com.kaoyan.ai.service.DomainContext;
import com.kaoyan.ai.service.PlanService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudySupervisionService {
  private static final String TABLE = "study_supervision";
  private static final String TASK = "task:";
  private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MM-dd HH:mm");
  private final RecordRepository db;
  private final EventService events;
  private final PlanService plans;
  private final StudyFocusService focus;
  private final AvatarService avatars;

  public StudySupervisionService(RecordRepository db, EventService events, PlanService plans, StudyFocusService focus,
      AvatarService avatars) {
    this.db = db;
    this.events = events;
    this.plans = plans;
    this.focus = focus;
    this.avatars = avatars;
  }

  private StudyMode mode(String user) {
    return StudyMode.parse(avatars.studyMode(user).toUpperCase(Locale.ROOT));
  }

  public ObjectNode settings(String user) {
    return obj("mode", mode(user).name());
  }

  @Transactional
  public ObjectNode saveMode(String user, String value) {
    StudyMode selected = StudyMode.parse(value);
    db.lockOwner(user);
    avatars.saveStudyMode(user, selected.name().toLowerCase(Locale.ROOT));
    return obj("mode", selected.name());
  }

  private void store(String user, String id, ObjectNode row) {
    int version = row.path("version").asInt(0);
    if (version == 0) version = db.find(TABLE, id, user).map(n -> n.path("version").asInt()).orElse(0);
    row.put("version", version + 1);
    if (version == 0) db.create(TABLE, id, user, row);
    else db.update(TABLE, id, user, row, version);
  }

  private ObjectNode plan(String user) {
    return db.find("profiles", user, user)
        .flatMap(p -> db.find("plans", p.path("activePlanId").asText(), user))
        .filter(p -> p.path("status").asText().equals("active")).orElseGet(() -> obj("tasks", arr()));
  }

  private List<JsonNode> tasks(ObjectNode plan) {
    return StreamSupport.stream(plan.path("tasks").spliterator(), false).toList();
  }

  private Map<String, ObjectNode> metadata(String user) {
    return db.list(TABLE, user).stream().filter(n -> n.path("key").asText().startsWith(TASK))
        .collect(Collectors.toMap(n -> n.path("key").asText().substring(TASK.length()), Function.identity()));
  }

  private static ObjectNode emptyTask(String id) {
    return obj("key", TASK + id, "receipts", obj());
  }

  private static boolean pending(JsonNode task) {
    return Set.of("pending", "overdue").contains(task.path("status").asText());
  }

  private static Instant instant(JsonNode node, String key) {
    return node.hasNonNull(key) ? Instant.parse(node.path(key).asText()) : null;
  }

  private static boolean unstarted(JsonNode task, JsonNode meta) {
    return pending(task) && !meta.hasNonNull("startedAt");
  }

  private static String title(JsonNode task) { return task.path("content").asText(); }
  private static String taskId(JsonNode task) { return task.path("id").asText(); }
  private static String display(Instant value) { return DISPLAY.format(value.atZone(ZONE)); }

  private String step(JsonNode task) {
    String text = title(task);
    if (text.contains("长难句")) return "先读一句，找出主谓宾";
    if (text.contains("错题") || text.contains("练习")) return "先读一道题，圈出已知条件";
    if (text.contains("单词") || text.contains("词汇")) return "先挑一个单词，读一遍并回忆词义";
    return "先打开这项任务的材料，读第一小段并圈出一个关键词";
  }

  private JsonNode recommend(JsonNode original, List<JsonNode> tasks, Map<String, ObjectNode> meta, Instant now) {
    if (!original.path("priority").isIntegralNumber() || original.path("minutes").asInt() <= 0) return original;
    return tasks.stream().filter(t -> t.path("priority").isIntegralNumber() && t.path("minutes").asInt() > 0)
        .filter(t -> {
          var data = meta.getOrDefault(taskId(t), emptyTask(taskId(t)));
          Instant schedule = instant(data, "scheduledAt");
          return unstarted(t, data) && (schedule != null ? !schedule.isAfter(now)
              : t.path("scheduledDate").asText().compareTo(now.atZone(ZONE).toLocalDate().toString()) <= 0);
        })
        .max(Comparator.comparingInt(t -> t.path("priority").asInt())).orElse(original);
  }

  private ObjectNode card(StudyMode mode, JsonNode source, JsonNode target, Instant schedule, int round) {
    return obj("taskId", taskId(target), "sourceTaskId", taskId(source), "title", title(target),
        "scheduledAt", schedule.toString(), "scheduledLabel", display(schedule), "round", round,
        "action", mode.action, "text", mode.reminder(title(target), display(schedule), step(target)),
        "priority", target.path("priority").isIntegralNumber() ? target.get("priority") : null,
        "minutes", target.path("minutes").asInt() > 0 ? target.path("minutes").asInt() : null);
  }

  // Called with the same owner lock as plan writes and mode changes.
  void scan(String user, ObjectNode plan, Instant now) {
    if (!plan.path("status").asText().equals("active")) return;
    StudyMode selected = mode(user);
    var meta = metadata(user);
    if (meta.isEmpty()) return;
    var tasks = tasks(plan);
    for (JsonNode task : tasks) {
      String id = taskId(task);
      var info = meta.get(id);
      if (info == null || !unstarted(task, info)) continue;
      Instant start = instant(info, "scheduledAt");
      if (start == null) continue;
      ObjectNode receipt = info.withObject("/receipts").withObjectProperty(start.toString());
      int round = selected.round(start, now, instant(receipt, "first"), receipt.has("second"));
      if (round == 0) continue;
      var target = selected == StudyMode.SCHOLAR ? recommend(task, tasks, meta, now) : task;
      var card = card(selected, task, target, start, round);
      String text = card.path("text").asText();
      String eventId = events.publish(user, "task.reminder", id, task.path("version").asInt(),
          obj("planId", plan.get("id"), "taskId", id, "scheduledDate", task.get("scheduledDate"), "message", text));
      events.notify(user, "task.reminder", id, "学习任务提醒", text, eventId);
      receipt.put(round == 1 ? "first" : "second", now.toString());
      receipt.put("round", round);
      store(user, TASK + id, info);
    }
  }

  @Transactional(readOnly = true)
  public ObjectNode state(String user, LocalDate date) {
    var plan = plan(user);
    var tasks = tasks(plan);
    var meta = metadata(user);
    var mode = mode(user);
    var now = Instant.now();
    var cards = arr();
    var timers = arr();
    var day = arr();
    var remainingTitles = new ArrayList<String>(3);
    JsonNode completed = null;
    int total = 0, done = 0;
    Instant nearest = null;
    for (JsonNode task : tasks) {
      String id = taskId(task);
      var info = meta.getOrDefault(id, emptyTask(id));
      Instant start = instant(info, "scheduledAt");
      if (pending(task) || "completed".equals(task.path("status").asText())) total++;
      if ("completed".equals(task.path("status").asText())) {
        done++;
        if (task.hasNonNull("completedAt") && (completed == null
            || instant(task, "completedAt").isAfter(instant(completed, "completedAt")))) completed = task;
      }
      if (!pending(task)) continue;
      if (remainingTitles.size() < 3) remainingTitles.add(title(task));
      if (task.path("scheduledDate").asText().equals(date.toString()))
        day.add(obj("id", id, "title", title(task), "scheduledAt", info.get("scheduledAt"),
            "started", info.hasNonNull("startedAt")));
      if (start != null && !start.isBefore(now) && (nearest == null || start.isBefore(nearest))) nearest = start;
      if (info.hasNonNull("startedAt")) {
        timers.add(obj("id", id, "title", title(task), "focusStart", info.get("focusStart"),
            "focusEnd", info.get("focusEnd")));
        continue;
      }
      if (start == null) continue;
      var receipt = info.path("receipts").path(start.toString());
      int round = receipt.path("round").asInt();
      if (round > receipt.path("dismissed").asInt()) {
        var target = mode == StudyMode.SCHOLAR ? recommend(task, tasks, meta, now) : task;
        cards.add(card(mode, task, target, start, round));
      }
    }
    ObjectNode feedback = completed == null ? null : feedback(user, mode, completed, done, total,
        remainingTitles, nearest, plan.path("targetDate").asText());
    return obj("mode", mode.name(), "date", date.toString(), "tasks", day, "reminders", cards,
        "timers", timers, "feedback", feedback);
  }

  private ObjectNode feedback(String user, StudyMode mode, JsonNode task, int done, int total,
      List<String> remainingTitles, Instant nearest, String deadline) {
    String title = title(task);
    String id = taskId(task);
    String progress = "已完成 " + done + "/" + total + " 项任务。";
    String text = switch (mode) {
      case SCHOLAR -> {
        double minutes = db.list("study_events", user).stream()
            .filter(e -> e.path("event").path("taskId").asText().equals(id))
            .mapToDouble(e -> e.path("effectiveMinutes").asDouble()).sum();
        yield "已完成「" + title + "」。" + (minutes >= 1 ? "实际学习 " + Math.round(minutes) + " 分钟。"
            : minutes > 0 ? "实际学习不足 1 分钟。" : progress);
      }
      case GENTLE -> "你完成了「" + title + "」，又推进了一项学习计划。可以继续学一小步，也可以先休息。";
      case STRICT -> "「" + title + "」已完成。剩余 " + (total - done) + " 项任务。"
          + (remainingTitles.isEmpty() ? "" : "待办：" + String.join("、", remainingTitles)
              + (total - done > remainingTitles.size() ? "等。" : "。"))
          + (nearest != null ? "最近计划时间：" + display(nearest) + "。" : total > done && !deadline.isBlank() ? "计划截止：" + deadline + "。" : "");
    };
    return obj("id", id, "text", text);
  }

  @Transactional
  public ObjectNode act(String user, String taskId, JsonNode body) {
    db.lockOwner(user);
    var plan = plan(user);
    var task = (ObjectNode) tasks(plan).stream().filter(t -> taskId(t).equals(taskId))
        .findFirst().orElseThrow(ApiException::missing);
    if (!pending(task)) throw ApiException.state("任务已完成或已取消，请刷新");
    String key = TASK + taskId;
    var info = db.find(TABLE, key, user).orElseGet(() -> emptyTask(taskId));
    Instant now = Instant.now();
    String action = body.path("action").asText();
    switch (action) {
      case "SCHEDULE" -> {
        if (!unstarted(task, info)) throw ApiException.state("已开始的任务不能改期");
        Instant schedule;
        try {
          schedule = OffsetDateTime.parse(body.path("scheduledAt").asText()).toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        } catch (DateTimeException ex) {
          throw new ApiException(422, "VALIDATION_FAILED", "请选择有效的计划开始时间");
        }
        LocalDate date = schedule.atZone(ZONE).toLocalDate();
        require(!date.isAfter(LocalDate.parse(plan.path("targetDate").asText()))
            && !date.isBefore(today().minusYears(1)), "任务时间必须在最近一年至目标日期之间");
        if (!date.toString().equals(task.path("scheduledDate").asText())) {
          int version = plan.path("version").asInt();
          task.put("scheduledDate", date.toString());
          task.put("version", task.path("version").asInt() + 1);
          plan.put("version", version + 1);
          db.update("plans", plan.path("id").asText(), user, plan, version);
        }
        info.put("scheduledAt", schedule.toString());
      }
      case "DISMISS" -> {
        var start = instant(info, "scheduledAt");
        if (start != null && start.toString().equals(body.path("scheduledAt").asText())) {
          var receipt = info.withObject("/receipts").withObjectProperty(start.toString());
          int round = body.path("round").asInt();
          if (round > receipt.path("dismissed").asInt() && round <= receipt.path("round").asInt())
            receipt.put("dismissed", round);
        }
      }
      case "START", "CONTINUE" -> {
        boolean start = action.equals("START");
        if (start != unstarted(task, info)) throw ApiException.state("任务状态已变化，请刷新");
        Instant end = instant(info, "focusEnd");
        if (end != null && now.isBefore(end)) throw ApiException.state("当前专注仍在计时");
        focus.finish(user, taskId, info, now);
        int limit = start ? mode(user).minutes : 0;
        if (limit == 0) limit = Math.max(1, Math.min(1440, task.path("minutes").asInt(25)));
        if (start) info.put("startedAt", now.toString());
        info.put("focusStart", now.toString());
        info.put("focusEnd", now.plusSeconds(limit * 60L).toString());
        info.put("focusId", id());
      }
      case "STOP" -> focus.finish(user, taskId, info, now);
      case "COMPLETE" -> {
        plans.execute("op055", new DomainContext(user, Map.of("id", plan.path("id").asText(), "taskId", taskId),
            obj(), obj("version", task.get("version"))));
        return obj("ok", true);
      }
      default -> throw new ApiException(422, "VALIDATION_FAILED", "不支持此任务操作");
    }
    store(user, key, info);
    return obj("ok", true);
  }
}
