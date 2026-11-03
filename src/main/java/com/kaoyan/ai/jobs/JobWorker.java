package com.kaoyan.ai.jobs;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.contract.ContractRegistry;
import com.kaoyan.ai.domain.*;
import com.kaoyan.ai.persistence.RecordRepository;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JobWorker {
  private final RecordRepository db;
  private final DomainDispatcher domain;
  private final EventService events;
  private final ContractRegistry contract;
  private final TransactionTemplate tx;
  private final Set<String> active = ConcurrentHashMap.newKeySet();
  private final ExecutorService executor =
      Executors.newFixedThreadPool(
          2,
          r -> {
            Thread t = new Thread(r, "study-job");
            t.setDaemon(true);
            return t;
          });

  public JobWorker(
      RecordRepository db,
      DomainDispatcher domain,
      EventService events,
      ContractRegistry contract,
      PlatformTransactionManager manager) {
    this.db = db;
    this.domain = domain;
    this.events = events;
    this.contract = contract;
    this.tx = new TransactionTemplate(manager);
  }

  @Scheduled(fixedDelay = 500)
  public void poll() {
    if (active.size() >= 2) return;
    var rows =
        db.jdbc()
            .query(
                "SELECT owner_id,payload FROM jobs WHERE payload LIKE '%queued%' ORDER BY"
                    + " created_at LIMIT 10",
                (rs, n) -> obj("owner", rs.getString(1), "job", parse(rs.getString(2))));
    for (JsonNode row : rows) {
      JsonNode job = row.get("job");
      if (!job.path("status").asText().equals("queued")) continue;
      String jid = job.path("id").asText();
      if (active.size() >= 2) break;
      if (active.add(jid))
        executor.execute(
            () -> {
              try {
                run(row.path("owner").asText(), jid);
              } finally {
                active.remove(jid);
              }
            });
    }
  }

  private void run(String user, String jid) {
    Boolean started =
        tx.execute(
            s -> {
              db.lockOwner(user);
              ObjectNode job = db.get("jobs", jid, user);
              if (!job.path("status").asText().equals("queued")) return false;
              int v = job.path("version").asInt();
              job.put("status", "running");
              job.put("version", v + 1);
              job.put("updatedAt", now());
              db.update("jobs", jid, user, job, v);
              return true;
            });
    if (!Boolean.TRUE.equals(started)) return;
    ObjectNode command = db.get("job_commands", jid, user);
    Map<String, String> paths = new HashMap<>();
    command
        .path("paths")
        .fields()
        .forEachRemaining(e -> paths.put(e.getKey(), e.getValue().asText()));
    DomainContext c = new DomainContext(user, paths, command.get("query"), command.get("body"));
    try {
      String op = command.path("operation").asText();
      JsonNode modelResult =
          domain.requiresModel(op) ? domain.complete(op, c, db.get("jobs", jid, user)) : null;
      tx.executeWithoutResult(
          s -> {
            db.lockOwner(user);
            ObjectNode job = db.get("jobs", jid, user);
            if (job.path("status").asText().equals("cancelled")) return;
            JsonNode result =
                modelResult == null
                    ? domain.complete(op, c, job)
                    : domain.persistModelResult(op, c, modelResult);
            int v = job.path("version").asInt();
            job.put("status", "completed");
            job.put("version", v + 1);
            job.put("progress", 100);
            job.set("result", result);
            job.putNull("error");
            job.put("updatedAt", now());
            if (job.path("resourceId").isNull()) {
              if (result.has("id")) job.set("resourceId", result.get("id"));
              else if (result.has("plan")) job.set("resourceId", result.path("plan").get("id"));
            }
            contract.model("Job", job);
            db.update("jobs", jid, user, job, v);
            String resource =
                job.path("resourceId").isNull() ? jid : job.path("resourceId").asText();
            events.publish(
                user,
                "ai.completed",
                resource,
                job.path("version").asInt(),
                obj(
                    "jobId",
                    jid,
                    "messageId",
                    job.path("kind").asText().equals("qa") ? resource : null,
                    "resultUrl",
                    job.get("resultUrl")));
            if (result.path("kind").asText().equals("examScore")
                && result.path("scoringStatus").asText().equals("completed")) {
              String eid =
                  events.publish(
                      user,
                      "exam.scored",
                      resource,
                      result.path("version").asInt(),
                      obj(
                          "sessionId",
                          resource,
                          "scoreSnapshotId",
                          result.get("scoreSnapshotId"),
                          "resultUrl",
                          "/api/v1/mock-exams/sessions/" + resource + "/result"));
              events.notify(user, "exam.scored", resource, "模考批改完成", "查看评分及薄弱知识点。", eid);
            }
          });
    } catch (Exception e) {
      tx.executeWithoutResult(
          s -> {
            db.lockOwner(user);
            ObjectNode job = db.get("jobs", jid, user);
            if (job.path("status").asText().equals("cancelled")) return;
            boolean retry = e instanceof ApiException a && (a.status() == 503 || a.status() == 504);
            String code =
                e instanceof ApiException a
                        && Set.of("DEPENDENCY_UNAVAILABLE", "PROVIDER_TIMEOUT").contains(a.code())
                    ? a.code()
                    : e instanceof ApiException ? "VALIDATION_FAILED" : "INTERNAL_ERROR";
            String message = e instanceof ApiException ? e.getMessage() : "任务执行失败，请查询服务日志";
            ObjectNode error = obj("code", code, "message", message, "retryable", retry);
            int v = job.path("version").asInt();
            job.put("status", retry ? "failed_retryable" : "failed");
            job.put("version", v + 1);
            job.put("updatedAt", now());
            job.set("error", error);
            db.update("jobs", jid, user, job, v);
            String resource =
                job.path("resourceId").isNull() ? jid : job.path("resourceId").asText();
            if (job.path("kind").asText().equals("qa")) {
              ObjectNode m = db.get("qa_messages", resource, user);
              int mv = m.path("version").asInt();
              m.put("status", retry ? "failed_retryable" : "failed");
              m.put("version", mv + 1);
              db.update("qa_messages", resource, user, m, mv);
            }
            if (job.path("kind").asText().equals("interviewReport")) {
              ObjectNode report = db.get("interview_reports", resource, user);
              int rv = report.path("version").asInt();
              report.put("status", retry ? "failed_retryable" : "failed");
              report.put("version", rv + 1);
              db.update("interview_reports", resource, user, report, rv);
            }
            if (e instanceof PartialScoringException partial)
              domain.persistModelResult("op061", c, partial.partial());
            if (job.path("kind").asText().equals("examScore")) {
              ObjectNode r = db.get("exam_results", resource, user);
              int rv = r.path("version").asInt();
              r.put("scoringStatus", retry ? "failed_retryable" : "failed");
              r.put("version", rv + 1);
              db.update("exam_results", resource, user, r, rv);
            }
            events.publish(
                user,
                "job.failed",
                resource,
                job.path("version").asInt(),
                obj("jobId", jid, "error", error));
          });
    }
  }

  @jakarta.annotation.PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }
}
