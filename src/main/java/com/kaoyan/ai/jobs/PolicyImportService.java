package com.kaoyan.ai.jobs;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.contract.ContractRegistry;
import com.kaoyan.ai.persistence.RecordRepository;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 读取本机明确配置、经人工核实的政策 JSON；不捏造报名日期或抓取来源。 */
@Component
public class PolicyImportService {
  private final String source;
  private final RecordRepository db;
  private final ContractRegistry contract;
  private final EventService events;

  public PolicyImportService(
      @Value("${app.policy-file:}") String source,
      RecordRepository db,
      ContractRegistry contract,
      EventService events) {
    this.source = source;
    this.db = db;
    this.contract = contract;
    this.events = events;
  }

  @Scheduled(fixedDelay = 60000)
  @Transactional
  public void refresh() throws Exception {
    if (source.isBlank()) return;
    Path file = Path.of(source);
    require(Files.size(file) <= 5000000, "政策文件过大");
    JsonNode feed = parse(Files.readString(file));
    require(feed.isObject() && feed.path("notices").isArray(), "政策文件须包含 notices 数组");
    for (JsonNode notice : feed.path("notices")) {
      contract.model("Notice", notice);
      require(!notice.path("sources").isEmpty(), "真实政策必须提供可核查来源");
      String nid = notice.path("id").asText();
      var previous = db.find("notices", nid, "public");
      if (previous.isPresent() && notice.equals(previous.get())) continue;
      if (previous.isPresent()) {
        int old = previous.get().path("version").asInt();
        require(notice.path("version").asInt() == old + 1, "政策更新版本必须连续递增");
        db.update("notices", nid, "public", (ObjectNode) notice, old);
      } else {
        require(notice.path("version").asInt() == 1, "新政策版本应为1");
        db.create("notices", nid, "public", notice);
      }
      db.create("notice_revisions", nid + ":" + notice.path("version").asInt(), "public", notice);
      for (String uid : db.jdbc().queryForList("SELECT id FROM app_users", String.class)) {
        var follows = db.find("news_follows", uid, uid);
        boolean match = notice.path("programId").isNull() && notice.path("region").isNull();
        if (follows.isPresent()) {
          JsonNode f = follows.get();
          if (f.path("year").asInt() != notice.path("year").asInt()) continue;
          for (JsonNode p : f.path("programIds"))
            if (p.equals(notice.path("programId"))) match = true;
          if (!f.path("region").isNull() && f.path("region").equals(notice.path("region")))
            match = true;
        }
        if (!match) continue;
        db.lockOwner(uid);
        String notification = id();
        String eventId =
            events.publish(
                uid,
                "policy.updated",
                nid,
                notice.path("version").asInt(),
                obj(
                    "noticeId",
                    nid,
                    "notificationId",
                    notification,
                    "noticeVersion",
                    notice.get("version")));
        db.create(
            "notifications",
            notification,
            uid,
            obj(
                "id",
                notification,
                "eventId",
                eventId,
                "type",
                "policy.updated",
                "title",
                notice.get("title"),
                "content",
                "政策信息已更新，请核对官方来源。",
                "resourceId",
                nid,
                "read",
                false,
                "readAt",
                null,
                "createdAt",
                now(),
                "version",
                1));
      }
    }
    for (JsonNode m : feed.path("milestones")) {
      contract.model("Milestone", m);
      require(!m.path("verified").asBoolean() || !m.path("sources").isEmpty(), "已核实时间提醒须提供来源");
      String mid = m.path("id").asText();
      var old = db.find("milestones", mid, "public");
      if (old.isEmpty()) db.create("milestones", mid, "public", m);
      else if (!old.get().equals(m)) {
        require(m.path("version").asInt() == old.get().path("version").asInt() + 1, "时间提醒版本必须连续递增");
        db.update("milestones", mid, "public", (ObjectNode) m, old.get().path("version").asInt());
      }
    }
  }
}
