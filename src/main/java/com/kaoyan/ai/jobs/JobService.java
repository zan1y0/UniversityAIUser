package com.kaoyan.ai.jobs;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.domain.*;
import com.kaoyan.ai.persistence.RecordRepository;
import org.springframework.stereotype.Service;

@Service
public class JobService {
  private final RecordRepository db;

  public JobService(RecordRepository db) {
    this.db = db;
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
    return obj("jobId", jid, "status", "queued", "resultUrl", url, "resourceId", resource);
  }

  public ObjectNode get(String uid, String jid) {
    return db.get("jobs", jid, uid);
  }
}
