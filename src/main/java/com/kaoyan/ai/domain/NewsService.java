package com.kaoyan.ai.domain;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.RecordRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class NewsService {
  private final RecordRepository db;
  private final CatalogService catalog;

  public NewsService(RecordRepository db, CatalogService catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  private ObjectNode follows(String uid) {
    var old = db.find("news_follows", uid, uid);
    if (old.isPresent()) return old.get();
    ObjectNode n =
        obj("programIds", arr(), "region", null, "year", today().getYear() + 1, "version", 1);
    db.create("news_follows", uid, uid, n);
    return n;
  }

  public JsonNode execute(String op, DomainContext c) {
    String user = c.user();
    JsonNode q = c.query();
    return switch (op) {
      case "op075" ->
          page(
              catalog.records("notices").stream()
                  .filter(x -> x.path("year").asInt() == q.path("year").asInt())
                  .filter(
                      x ->
                          !q.has("programId")
                              || x.path("programId").asText().equals(c.q("programId")))
                  .filter(x -> !q.has("region") || x.path("region").asText().equals(c.q("region")))
                  .toList(),
              q);
      case "op076" -> {
        ObjectNode notice = db.publicGet("notices", c.path("id"));
        int from = q.path("fromVersion").asInt(), to = q.path("toVersion").asInt();
        require(from <= to, "版本范围错误");
        JsonNode a = db.publicGet("notice_revisions", c.path("id") + ":" + from),
            b = db.publicGet("notice_revisions", c.path("id") + ":" + to);
        ArrayNode changes = arr();
        for (String key : List.of("title", "content", "publishedAt", "sources"))
          if (!a.path(key).equals(b.path(key)))
            changes.add(
                obj(
                    "field",
                    key,
                    "before",
                    a.path(key).isTextual() ? a.path(key).asText() : a.path(key).toString(),
                    "after",
                    b.path(key).isTextual() ? b.path(key).asText() : b.path(key).toString()));
        yield obj(
            "noticeId",
            c.path("id"),
            "fromVersion",
            from,
            "toVersion",
            to,
            "changes",
            changes,
            "sources",
            notice.get("sources"));
      }
      case "op077" -> follows(user);
      case "op078" -> {
        ObjectNode previous = follows(user);
        int v = previous.path("version").asInt();
        version(previous, c.integer("version"));
        for (JsonNode p : c.body().path("programIds")) catalog.program(p.asText());
        ObjectNode next = (ObjectNode) c.body().deepCopy();
        next.put("version", v + 1);
        db.update("news_follows", user, user, next, v);
        yield next;
      }
      case "op079" ->
          obj(
              "items",
              catalog.records("milestones").stream()
                  .filter(x -> x.path("year").asInt() == q.path("year").asInt())
                  .filter(x -> !q.has("region") || x.path("region").asText().equals(c.q("region")))
                  .filter(
                      x ->
                          !q.has("programId")
                              || x.path("programId").asText().equals(c.q("programId")))
                  .toList());
      case "op080" ->
          page(
              db.list("notifications", user).stream()
                  .filter(x -> !q.path("unreadOnly").asBoolean() || !x.path("read").asBoolean())
                  .filter(
                      x ->
                          !q.has("afterEventId")
                              || new java.math.BigInteger(x.path("eventId").asText())
                                      .compareTo(new java.math.BigInteger(c.q("afterEventId")))
                                  > 0)
                  .toList(),
              q);
      case "op081" -> {
        ObjectNode n = db.get("notifications", c.path("id"), user);
        version(n, c.integer("version"));
        int v = n.path("version").asInt();
        n.put("read", true);
        n.put("readAt", now());
        n.put("version", v + 1);
        db.update("notifications", c.path("id"), user, n, v);
        yield n;
      }
      default -> throw new IllegalArgumentException(op);
    };
  }
}
