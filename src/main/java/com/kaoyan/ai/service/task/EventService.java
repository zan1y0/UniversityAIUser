package com.kaoyan.ai.service.task;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.api.contract.ContractRegistry;
import com.kaoyan.ai.persistence.repository.EventOutboxRepository;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.kaoyan.ai.service.auth.SessionService;
import com.kaoyan.ai.service.auth.JwtService;
import com.kaoyan.ai.service.auth.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class EventService {
  private final RecordRepository db;
  private final SessionService sessions;
  private final ContractRegistry contract;
  private final EventOutboxRepository outbox;
  private final JwtService jwt;
  private final PermissionService permissions;

  private static class Client {
    final String user;
    final SseEmitter emitter;
    final jakarta.servlet.http.HttpSession session;
    final String sessionId;
    final String token;
    long cursor;
    long ping;

    Client(String u, SseEmitter e, long c, jakarta.servlet.http.HttpSession s, String token) {
      user = u;
      emitter = e;
      cursor = c;
      session = s;
      sessionId = s == null ? null : s.getId();
      this.token = token;
      ping = System.currentTimeMillis();
    }
  }

  private final List<Client> clients = new CopyOnWriteArrayList<>();

  public EventService(
      RecordRepository db,
      SessionService sessions,
      ContractRegistry contract,
      EventOutboxRepository outbox, JwtService jwt, PermissionService permissions) {
    this.db = db;
    this.sessions = sessions;
    this.contract = contract;
    this.outbox = outbox;
    this.jwt = jwt;
    this.permissions = permissions;
  }

  @Transactional
  public String publish(String user, String type, String resource, int version, JsonNode data) {
    String eventId = Long.toString(outbox.reserve(user, type));
    ObjectNode n =
        obj(
            "eventId",
            eventId,
            "type",
            type,
            "resourceId",
            resource,
            "version",
            version,
            "occurredAt",
            now(),
            "data",
            data);
    contract.model("EventPayload", n);
    outbox.complete(Long.parseLong(eventId), user, n);
    return eventId;
  }

  public void notify(
      String user, String type, String resource, String title, String text, String eventId) {
    ObjectNode n =
        obj(
            "id",
            id(),
            "eventId",
            eventId,
            "type",
            type,
            "title",
            title,
            "content",
            text,
            "resourceId",
            resource,
            "read",
            false,
            "readAt",
            null,
            "createdAt",
            now(),
            "version",
            1);
    db.create("notifications", n.path("id").asText(), user, n);
  }

  public SseEmitter subscribe(HttpServletRequest r) {
    String user = sessions.user(r);
    if (clients.stream().filter(x -> x.user.equals(user)).count() >= 5)
      throw new ApiException(429, "RATE_LIMITED", "事件连接过多");
    long cursor = 0;
    String raw = r.getHeader("Last-Event-ID");
    if (raw != null) {
      try {
        if (!raw.matches("^[1-9][0-9]{0,19}$")) throw new IllegalArgumentException();
        cursor = Long.parseLong(raw);
      } catch (Exception e) {
        throw new ApiException(400, "INVALID_REQUEST", "Last-Event-ID 不合法");
      }
    } else cursor = outbox.latest(user);
    SseEmitter emitter = new SseEmitter(25 * 60 * 1000L);
    Client c = new Client(user, emitter, cursor, r.getSession(false), jwt.token(r).orElse(null));
    clients.add(c);
    emitter.onCompletion(() -> clients.remove(c));
    emitter.onTimeout(
        () -> {
          clients.remove(c);
          emitter.complete();
        });
    emitter.onError(e -> clients.remove(c));
    try {
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (Exception e) {
      clients.remove(c);
    }
    return emitter;
  }

  @Scheduled(fixedDelay = 1000)
  public void deliver() {
    for (Client c : clients) {
      try {
        var rows = outbox.after(c.user, c.cursor);
        boolean heartbeat = System.currentTimeMillis() - c.ping > 15000;
        if (rows.isEmpty() && !heartbeat) continue;
        java.util.List<String> granted;
        if (c.token != null) {
          var identity = jwt.verify(c.token);
          granted = permissions.validate(c.user, identity.getClaimAsString("activeRole"),
              identity.getClaims().get("authVersion"));
        } else {
          if (c.session == null || !c.user.equals(c.session.getAttribute("userId"))
              || !c.sessionId.equals(c.session.getId()))
            throw new ApiException(401, "UNAUTHENTICATED", "登录已过期");
          granted = permissions.validate(c.user, (String) c.session.getAttribute("activeRole"),
              c.session.getAttribute("authVersion"));
        }
        if (!granted.contains("student:study:use"))
          throw new ApiException(403, "FORBIDDEN", "事件订阅权限已撤销");
        for (var row : rows) {
          c.emitter.send(
              SseEmitter.event().id(Long.toString(row.id())).name(row.type()).data(row.payload()));
          c.cursor = row.id();
        }
        if (heartbeat) {
          c.emitter.send(SseEmitter.event().comment("heartbeat"));
          c.ping = System.currentTimeMillis();
        }
      } catch (Exception e) {
        clients.remove(c);
        c.emitter.complete();
      }
    }
  }
}
