package com.kaoyan.ai.domain;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.persistence.RecordRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {
  private final RecordRepository db;

  public ProfileService(RecordRepository db) {
    this.db = db;
  }

  public ObjectNode get(String user) {
    return db.get("profiles", user, user);
  }

  public ObjectNode update(DomainContext c) {
    ObjectNode p = get(c.user());
    version(p, c.integer("version"));
    if (!c.body().path("targetProgramId").isNull())
      db.publicGet("programs", c.b("targetProgramId"));
    for (JsonNode b : c.body().path("baseline")) {
      require(b.path("score").asDouble() <= b.path("maxScore").asDouble(), "基础分数不得超过满分");
    }
    int old = p.path("version").asInt();
    c.body().fields().forEachRemaining(e -> p.set(e.getKey(), e.getValue()));
    p.put("version", old + 1);
    db.update("profiles", c.user(), c.user(), p, old);
    return p;
  }
}
