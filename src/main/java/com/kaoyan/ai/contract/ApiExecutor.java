package com.kaoyan.ai.contract;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.domain.*;
import com.kaoyan.ai.persistence.IdempotencyRepository;
import com.kaoyan.ai.persistence.RecordRepository;
import com.kaoyan.ai.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiExecutor {
  private final ContractRegistry contract;
  private final SessionService sessions;
  private final RecordRepository db;
  private final DomainDispatcher domain;
  private final IdempotencyRepository idempotency;

  public ApiExecutor(
      ContractRegistry contract,
      SessionService sessions,
      RecordRepository db,
      DomainDispatcher domain,
      IdempotencyRepository idempotency) {
    this.contract = contract;
    this.sessions = sessions;
    this.db = db;
    this.domain = domain;
    this.idempotency = idempotency;
  }

  @Transactional
  public ResponseEntity<?> execute(
      String op, Map<String, String> paths, JsonNode body, HttpServletRequest r) {
    if (body == null) body = com.fasterxml.jackson.databind.node.NullNode.instance;
    contract.request(op, body, paths, r);
    ObjectNode query = contract.query(op, r);
    int status = contract.status(op);
    String uid = Set.of("op001", "op002").contains(op) ? null : sessions.user(r);
    boolean write = !r.getMethod().equals("GET");
    if (uid != null && write) db.lockOwner(uid);
    String key = r.getHeader("Idempotency-Key"), fingerprint = null;
    if (contract.idempotent(op)) {
      if (body.has("requestId"))
        require(body.path("requestId").asText().equals(key), "requestId 必须与 Idempotency-Key 一致");
      if (body.has("eventId"))
        require(body.path("eventId").asText().equals(key), "eventId 必须与 Idempotency-Key 一致");
      fingerprint =
          db.fingerprint(
              obj("paths", new TreeMap<>(paths), "query", query, "body", canonical(body)));
      var old = idempotency.find(uid, op, key);
      if (old.isPresent()) {
        var row = old.get();
        if (row.createdAt().plusSeconds(86400).isAfter(Instant.now())) {
          if (!fingerprint.equals(row.fingerprint()))
            throw new ApiException(409, "IDEMPOTENCY_MISMATCH", "相同幂等键不能提交不同请求");
          return ResponseEntity.status(row.statusCode()).body(parse(row.responseBody()));
        }
        idempotency.delete(uid, op, key);
      }
    }
    JsonNode data =
        switch (op) {
          case "op001" -> {
            r.getSession(true)
                .setAttribute(
                    "loginFailures",
                    r.getSession().getAttribute("loginFailures") == null
                        ? 0
                        : r.getSession().getAttribute("loginFailures"));
            yield obj("token", sessions.csrf(r), "headerName", "X-CSRF-Token");
          }
          case "op002" ->
              sessions.login(r, body.path("loginName").asText(), body.path("password").asText());
          case "op003" -> {
            sessions.logout(r);
            yield null;
          }
          default -> domain.execute(op, new DomainContext(uid, paths, query, body));
        };
    if (status == 204) return ResponseEntity.noContent().build();
    ObjectNode result =
        obj("code", "OK", "message", "操作成功", "data", data, "traceId", r.getAttribute("traceId"));
    contract.response(op, result);
    if (contract.idempotent(op))
      idempotency.save(uid, op, key, fingerprint, status, result.toString());
    return ResponseEntity.status(status).body(result);
  }

  private JsonNode canonical(JsonNode n) {
    if (n.isObject()) {
      ObjectNode out = obj();
      TreeMap<String, JsonNode> fields = new TreeMap<>();
      n.fields().forEachRemaining(e -> fields.put(e.getKey(), e.getValue()));
      fields.forEach((k, v) -> out.set(k, canonical(v)));
      return out;
    }
    if (n.isArray()) {
      ArrayNode out = arr();
      n.forEach(v -> out.add(canonical(v)));
      return out;
    }
    return n;
  }
}
