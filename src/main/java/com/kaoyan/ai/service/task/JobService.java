package com.kaoyan.ai.service.task;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.infrastructure.mq.JobQueued;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.kaoyan.ai.service.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class JobService {
  private final RecordRepository db;
  private final ApplicationEventPublisher publisher;

  public JobService(RecordRepository db, ApplicationEventPublisher publisher) {
    this.db = db;
    this.publisher = publisher;
  }

  public ObjectNode enqueue(String op, String kind, DomainContext c, String resource) {
    if (db.list("jobs", c.user()).stream()
            .filter(j -> java.util.Set.of("queued", "running").contains(j.path("status").asText()))
            .count()
        >= 20)
      throw new com.kaoyan.ai.common.ApiException(429, "RATE_LIMITED", "待处理任务过多，请等待已有任务完成");
    String jid = id(), url = "/api/v1/jobs/" + jid;
    ObjectNode job =
        obj(
            "id",
            jid,
            "kind",
            kind,
            "status",
            "queued",
            "version",
            1,
            "progress",
            0,
            "resourceId",
            resource,
            "resultUrl",
            url,
            "result",
            null,
            "error",
            null,
            "createdAt",
            now(),
            "updatedAt",
            now());
    db.create("jobs", jid, c.user(), job);
    db.create(
        "job_commands",
        jid,
        c.user(),
        obj(
            "jobId",
            jid,
            "operation",
            op,
            "paths",
            c.paths(),
            "query",
            c.query(),
            "body",
            c.body()));
    // 任务行已写入，事务提交后再投递到 MQ（MqPublisher 里用 AFTER_COMMIT 监听），
    // 消费方读到的任务一定是已提交的；MQ 不可用时仍由 JobWorker 轮询兜底。
    publisher.publishEvent(new JobQueued(c.user(), jid));
    return obj("jobId", jid, "status", "queued", "resultUrl", url, "resourceId", resource);
  }

  public ObjectNode get(String uid, String jid) {
    return db.get("jobs", jid, uid);
  }
}
