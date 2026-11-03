package com.kaoyan.ai.jobs;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.persistence.RecordRepository;
import java.time.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ReminderService {
  private final RecordRepository db;
  private final EventService events;
  private final TransactionTemplate tx;

  public ReminderService(
      RecordRepository db, EventService events, PlatformTransactionManager manager) {
    this.db = db;
    this.events = events;
    this.tx = new TransactionTemplate(manager);
  }

  @Scheduled(fixedDelay = 60000)
  public void remind() {
    for (var row :
        db.jdbc()
            .query(
                "SELECT owner_id,payload FROM plans",
                (rs, n) -> obj("user", rs.getString(1), "plan", parse(rs.getString(2))))) {
      JsonNode plan = row.get("plan");
      if (!plan.path("status").asText().equals("active")) continue;
      String user = row.path("user").asText();
      var pref = db.find("reminder_preferences", user, user);
      if (pref.isEmpty()
          || !pref.get().path("enabled").asBoolean()
          || LocalTime.now(ZONE).isBefore(LocalTime.parse(pref.get().path("time").asText())))
        continue;
      tx.executeWithoutResult(
          status -> {
            db.lockOwner(user);
            ObjectNode latest = db.get("plans", plan.path("id").asText(), user);
            for (JsonNode task : latest.path("tasks")) {
              String taskId = task.path("id").asText();
              if (task.path("status").asText().equals("completed")
                  || LocalDate.parse(task.path("scheduledDate").asText()).isAfter(today()))
                continue;
              boolean sent =
                  db.list("notifications", user).stream()
                      .anyMatch(
                          n ->
                              n.path("type").asText().equals("task.reminder")
                                  && n.path("resourceId").asText().equals(taskId)
                                  && Instant.parse(n.path("createdAt").asText())
                                      .atZone(ZONE)
                                      .toLocalDate()
                                      .equals(today()));
              if (sent) continue;
              String text =
                  LocalDate.parse(task.path("scheduledDate").asText()).isBefore(today())
                      ? "该学习任务已超时，请复盘进度并调整计划。"
                      : "到学习时间了，请按计划完成今天的任务。";
              String eventId =
                  events.publish(
                      user,
                      "task.reminder",
                      taskId,
                      task.path("version").asInt(),
                      obj(
                          "planId",
                          latest.get("id"),
                          "taskId",
                          taskId,
                          "scheduledDate",
                          task.get("scheduledDate"),
                          "message",
                          text));
              events.notify(user, "task.reminder", taskId, "学习任务提醒", text, eventId);
            }
          });
    }
  }
}
