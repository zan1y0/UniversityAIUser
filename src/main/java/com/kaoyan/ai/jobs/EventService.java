package com.kaoyan.ai.jobs;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.contract.ContractRegistry;
import com.kaoyan.ai.persistence.EventOutboxRepository;
import com.kaoyan.ai.persistence.RecordRepository;
import com.kaoyan.ai.security.SessionService;
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

  private static class Client {
    final String user;
    final SseEmitter emitter;
    final jakarta.servlet.http.HttpSession session;
    final String sessionId;
    long cursor;
    long ping;

    Client(String u, SseEmitter e, long c, jakarta.servlet.http.HttpSession s) {
      user = u;
      emitter = e;
      cursor = c;
      session = s;
      sessionId = s.getId();
      ping = System.currentTimeMillis();
    }
  }

  private final List<Client> clients = new CopyOnWriteArrayList<>();

  public EventService(
      RecordRepository db,
      SessionService sessions,
      ContractRegistry contract,
      EventOutboxRepository outbox) {
    this.db = db;
    this.sessions = sessions;
    this.contract = contract;
    this.outbox = outbox;
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
    Client c = new Client(user, emitter, cursor, r.getSession(false));
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
        if (!c.user.equals(c.session.getAttribute("userId"))
            || !c.sessionId.equals(c.session.getId())) {
          clients.remove(c);
          c.emitter.complete();
          continue;
        }
        var rows = outbox.after(c.user, c.cursor);
        for (var row : rows) {
          c.emitter.send(
              SseEmitter.event().id(Long.toString(row.id())).name(row.type()).data(row.payload()));
          c.cursor = row.id();
        }
        if (System.currentTimeMillis() - c.ping > 15000) {
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
