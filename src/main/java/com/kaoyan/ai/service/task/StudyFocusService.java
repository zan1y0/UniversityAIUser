package com.kaoyan.ai.service.task;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.kaoyan.ai.service.AnalyticsService;
import com.kaoyan.ai.service.DomainContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StudyFocusService {
  private final RecordRepository db;
  private final AnalyticsService analytics;

  public StudyFocusService(RecordRepository db, AnalyticsService analytics) {
    this.db = db;
    this.analytics = analytics;
  }

  // A completion through the existing study-plan interface closes the same timer.
  public void finishForCompletion(String user, String taskId, Instant now) {
    String key = "task:" + taskId;
    db.find("study_supervision", key, user).ifPresent(info -> {
      if (!info.hasNonNull("focusStart")) return;
      finish(user, taskId, info, now);
      int version = info.path("version").asInt();
      info.put("version", version + 1);
      db.update("study_supervision", key, user, info, version);
    });
  }

  void finish(String user, String taskId, ObjectNode info, Instant now) {
    if (!info.hasNonNull("focusStart")) return;
    Instant start = Instant.parse(info.path("focusStart").asText());
    Instant end = Instant.parse(info.path("focusEnd").asText());
    if (now.isBefore(end)) end = now;
    if (end.isAfter(start)) {
      analytics.execute("op071", new DomainContext(user, Map.of(), obj(), obj(
          "eventId", info.get("focusId"), "type", "reading", "startAt", start.toString(),
          "endAt", end.toString(), "taskId", taskId, "focusSelfReport", null)));
    }
    info.remove(List.of("focusStart", "focusEnd", "focusId"));
  }
}
