package com.kaoyan.ai.jobs;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.persistence.RecordRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 单机本地运行：恢复重启前未结束的任务，避免永久停留在 running。 */
@Component
public class JobRecovery {
  private final RecordRepository db;
  private final EventService events;

  public JobRecovery(RecordRepository db, EventService events) {
    this.db = db;
    this.events = events;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void recover() {
    for (var row :
        db.jdbc()
            .query(
                "SELECT owner_id,payload FROM jobs",
                (rs, n) -> obj("user", rs.getString(1), "job", parse(rs.getString(2))))) {
      ObjectNode job = (ObjectNode) row.get("job");
      if (!job.path("status").asText().equals("running")) continue;
      String user = row.path("user").asText();
      db.lockOwner(user);
      int v = job.path("version").asInt();
      ObjectNode error =
          obj(
              "code",
              "DEPENDENCY_UNAVAILABLE",
              "message",
              "服务已重启，该任务需重新提交；已保存的数据仍可恢复。",
              "retryable",
              true);
      job.put("status", "failed_retryable");
      job.put("version", v + 1);
      job.put("updatedAt", now());
      job.set("error", error);
      db.update("jobs", job.path("id").asText(), user, job, v);
      String resource = job.path("resourceId").asText();
      String table =
          job.path("kind").asText().equals("qa")
              ? "qa_messages"
              : job.path("kind").asText().equals("examScore")
                  ? "exam_results"
                  : job.path("kind").asText().equals("interviewReport")
                      ? "interview_reports"
                      : null;
      if (table != null) {
        ObjectNode n = db.get(table, resource, user);
        int nv = n.path("version").asInt();
        n.put(table.equals("exam_results") ? "scoringStatus" : "status", "failed_retryable");
        n.put("version", nv + 1);
        db.update(table, resource, user, n, nv);
      }
      if (job.path("resourceId").isNull()) resource = job.path("id").asText();
      events.publish(
          user,
          "job.failed",
          resource,
          job.path("version").asInt(),
          obj("jobId", job.get("id"), "error", error));
    }
  }
}
