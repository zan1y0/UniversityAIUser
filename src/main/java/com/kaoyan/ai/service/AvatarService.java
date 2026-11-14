package com.kaoyan.ai.service;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AvatarService {
  public static final String SHAPE = "11000000-0000-4000-8000-000000000001";
  private final RecordRepository db;

  public AvatarService(RecordRepository db) {
    this.db = db;
  }

  private ObjectNode defaultSettings(String table, String user) {
    var old = db.find(table, user, user);
    if (old.isPresent()) return old.get();
    ObjectNode n =
        table.equals("avatar_preferences")
            ? obj(
                "shapeId",
                SHAPE,
                "mode",
                "gentle",
                "voice",
                "zh-CN",
                "reminderTime",
                "20:00",
                "version",
                1)
            : obj("enabled", true, "time", "20:00", "mode", "gentle", "version", 1);
    return n;
  }

  public String studyMode(String user) {
    return defaultSettings("avatar_preferences", user).path("mode").asText("gentle");
  }

  // Callers that change settings already hold the owner lock.
  public void saveStudyMode(String user, String mode) {
    ObjectNode value = defaultSettings("avatar_preferences", user);
    if (value.path("mode").asText().equals(mode) &&
        db.find("avatar_preferences", user, user).isPresent()) return;
    value.put("mode", mode);
    saveSettings("avatar_preferences", user, value, value.path("version").asInt());
  }

  private void saveSettings(String table, String user, ObjectNode value, int version) {
    value.put("version", version + 1);
    if (db.find(table, user, user).isPresent()) db.update(table, user, user, value, version);
    else db.create(table, user, user, value);
  }

  public JsonNode execute(String op, DomainContext c) {
    return switch (op) {
      case "op011" ->
          obj(
              "items",
              !c.query().has("shapeId") || c.q("shapeId").equals(SHAPE)
                  ? arr(
                      obj(
                          "shapeId",
                          SHAPE,
                          "name",
                          "研途助教",
                          "previewUrl",
                          "http://127.0.0.1:8080/assets/avatar.svg",
                          "assetUrl",
                          "http://127.0.0.1:8080/assets/avatar.svg",
                          "actionCodes",
                          arr("idle", "speak", "encourage", "remind", "celebrate"),
                          "voices",
                          arr("zh-CN")))
                  : arr());
      case "op012" -> defaultSettings("avatar_preferences", c.user());
      case "op017" -> defaultSettings("reminder_preferences", c.user()).put("mode", studyMode(c.user()));
      case "op013", "op018" -> {
        String table = op.equals("op013") ? "avatar_preferences" : "reminder_preferences";
        ObjectNode p = defaultSettings(table, c.user());
        int v = p.path("version").asInt();
        version(p, c.integer("version"));
        if (op.equals("op013"))
          require(c.b("shapeId").equals(SHAPE) && c.b("voice").equals("zh-CN"), "形象或语音选项不存在");
        ObjectNode n = (ObjectNode) c.body().deepCopy();
        saveSettings(table, c.user(), n, v);
        if (op.equals("op018")) saveStudyMode(c.user(), c.b("mode"));
        yield n;
      }
      case "op015" -> {
        LocalDate date = LocalDate.parse(c.b("localDate"));
        require(date.equals(today()), "只能按当前中国标准日期打卡");
        String key = c.user() + ":" + date;
        var old = db.find("checkins", key, c.user());
        if (old.isPresent()) yield old.get();
        int streak = 1;
        var prev = db.find("checkins", c.user() + ":" + date.minusDays(1), c.user());
        if (prev.isPresent()) streak = Math.min(3660, prev.get().path("streakDays").asInt() + 1);
        ObjectNode n =
            obj("id", id(), "localDate", date.toString(), "streakDays", streak, "createdAt", now());
        db.create("checkins", key, c.user(), n);
        yield n;
      }
      case "op016" -> {
        int planned = 0, completed = 0;
        ArrayNode overdue = arr();
        LocalDate date = LocalDate.parse(c.q("date"));
        for (ObjectNode p : db.list("plans", c.user()))
          if (p.path("status").asText().equals("active"))
            for (JsonNode t : p.path("tasks")) {
              if (t.path("scheduledDate").asText().equals(date.toString())) {
                planned++;
                if (t.path("status").asText().equals("completed")) completed++;
              }
              if (!t.path("status").asText().equals("completed")
                  && LocalDate.parse(t.path("scheduledDate").asText()).isBefore(date))
                overdue.add(t.get("id"));
            }
        yield obj(
            "date",
            date.toString(),
            "plannedTasks",
            planned,
            "completedTasks",
            completed,
            "overdueTaskIds",
            overdue,
            "message",
            planned == 0 ? "先确认学习计划，再逐步推进。" : "今天已完成 " + completed + " / " + planned + " 项任务。",
            "mode",
            studyMode(c.user()));
      }
      default -> throw new IllegalArgumentException(op);
    };
  }
}
