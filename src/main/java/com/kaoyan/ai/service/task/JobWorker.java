package com.kaoyan.ai.service.task;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.api.contract.ContractRegistry;
import com.kaoyan.ai.infrastructure.mq.MqProperties;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.kaoyan.ai.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JobWorker {
  private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

  private final RecordRepository db;
  private final DomainDispatcher domain;
  private final EventService events;
  private final ContractRegistry contract;
  private final TransactionTemplate tx;
  private final boolean mqEnabled;
  private final long safetyScanMillis;
  private volatile long lastScanAt;
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
      PlatformTransactionManager manager,
      MqProperties mq) {
    this.db = db;
    this.domain = domain;
    this.events = events;
    this.contract = contract;
    this.tx = new TransactionTemplate(manager);
    this.mqEnabled = mq.isEnabled();
    this.safetyScanMillis = mq.getSafetyScanMillis();
  }

  @Scheduled(fixedDelay = 500)
  public void poll() {
    long now = System.currentTimeMillis();
    // MQ 开启后任务由消费者立即触发，轮询降级为兜底（默认 30 秒扫一次），避免空转扫表。
    if (mqEnabled && now - lastScanAt < safetyScanMillis) return;
    lastScanAt = now;
    if (active.size() >= 2) return;
    var rows =
        db.queuedJobs();
    for (JsonNode row : rows) {
      JsonNode job = row.get("job");
      if (!job.path("status").asText().equals("queued")) continue;
      String jid = job.path("id").asText();
      if (active.size() >= 2) break;
      dispatch(row.path("owner").asText(), jid);
    }
  }

  /**
   * 任务执行的唯一入口：轮询和 MQ 消费者都走这里。
   *
   * <p>同一实例内用 jobId 去重；跨实例靠 {@link #run} 里的事务抢占（只有把 queued 改成 running 成功的那次会执行），
   * 所以消息重复投递、或多个实例同时收到同一条消息都是安全的。
   *
   * <p>线程池满时不阻塞消费线程：消息被确认，任务留在 queued，下一次兜底扫描会重新捡起来。
   */
  public void dispatch(String user, String jid) {
    if (active.size() >= 2) {
      log.info("任务线程池已满，job={} 留给下一次兜底扫描", jid);
      return;
    }
    if (!active.add(jid)) return;
    try {
      executor.execute(
          () -> {
            try {
              run(user, jid);
            } finally {
              active.remove(jid);
            }
          });
    } catch (RejectedExecutionException e) {
      active.remove(jid);
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
