package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static com.kaoyan.ai.infrastructure.config.LocalDataInitializer.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP 模型替身仅用于测试，不提供运行时虚假预测。 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:kt-integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.local-student-password=LocalStudy2026!", "app.seed-local=true", "app.mq.enabled=false",
    "ai.kt.enabled=true", "ai.kt.model-version=kt-gkt-v1"
})
@AutoConfigureMockMvc
class KnowledgeTracingIntegrationTests {
  static final String KNOWLEDGE = "40000000-0000-4000-8000-000000000001";
  static final AtomicReference<JsonNode> received = new AtomicReference<>();
  static final HttpServer server = startServer();

  static HttpServer startServer() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/v1/mastery", exchange -> {
        received.set(parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        byte[] bytes = obj("modelVersion", "kt-gkt-v1", "knowledgeMastery", arr(
            obj("knowledgeId", KNOWLEDGE, "mastery", 0.72, "sampleCount", 1,
                "stddev", 0.1, "weight", 0.8, "source", "model")),
            "recommendedQuestions", arr()).toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.start();
      return server;
    } catch (java.io.IOException e) { throw new IllegalStateException(e); }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("ai.kt.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired RecordRepository records;
  MockHttpSession session;
  String csrf;

  JsonNode call(String method, String path, JsonNode body, int expected) throws Exception {
    var builder = request(HttpMethod.valueOf(method), "/api/v1" + path);
    if (session != null) builder.session(session);
    if (csrf != null) builder.header("X-CSRF-Token", csrf);
    if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body.toString());
    var result = mvc.perform(builder).andReturn();
    session = (MockHttpSession) result.getRequest().getSession(false);
    assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    return parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
  }

  @AfterAll static void stopServer() { server.stop(0); }

  @Test
  void realHttpModelThenClosedServicePreserves200LegacyContractAndAuditsResults() throws Exception {
    csrf = call("GET", "/session/csrf", null, 200).path("token").asText();
    call("POST", "/session/login", obj("email", "student@example.test", "password", "LocalStudy2026!"), 200);
    // 用测试数据库中的真实格式记录建立样本，模型数值只由上面的 HTTP 测试替身提供。
    String attempt = id();
    records.create("attempts", attempt, USER, obj("id", attempt, "sessionId", id(),
        "questionVersionId", "51000000-0000-4000-8000-000000000001", "subjectId", MATH,
        "knowledgeId", KNOWLEDGE, "correct", false, "score", 0, "maxScore", 5,
        "answer", obj("kind", "singleChoice", "optionId", "A"), "createdAt", now()));
    String path = "/practice/knowledge-progress?subjectId=" + MATH + "&timeRange=all";
    var progress = call("GET", path, null, 200);
    assertEquals("kt-gkt-v1", progress.path("algorithmVersion").asText());
    assertTrue(received.get().path("history").isArray());
    assertEquals(USER, received.get().path("userId").asText());
    assertEquals(0, received.get().path("history").path(0).path("correct").asInt());
    assertEquals(0.72, progress.path("items").path(0).path("mastery").asDouble());
    for (JsonNode item : progress.path("items")) assertEquals(4, item.size());
    var stored = jdbc.queryForMap("SELECT * FROM kt_prediction_results WHERE user_id=? AND operation_id='op049'", USER);
    assertEquals(progress, parse(jdbc.queryForObject(
        "SELECT result_json FROM kt_prediction_results WHERE user_id=? AND operation_id='op049'", String.class, USER)));
    assertEquals("kt-gkt-v1", stored.get("algorithm_version"));
    server.stop(0);
    // 新作答必须使 60 秒缓存立即失效，此时真实连接失败也不能使接口报错。
    String next = id();
    var copied = records.get("attempts", attempt, USER).deepCopy();
    copied.put("id", next).put("createdAt", now());
    records.create("attempts", next, USER, copied);
    var fallback = call("GET", path, null, 200);
    assertEquals("empirical-correct-rate-v1", fallback.path("algorithmVersion").asText());
    assertEquals(0.0, fallback.path("items").path(0).path("mastery").asDouble());
    var recommendations = call("GET", "/analytics/recommendations?subjectId=" + MATH, null, 200);
    assertEquals("empirical-correct-rate-v1", recommendations.path("algorithmVersion").asText());
    assertEquals("rule", recommendations.path("items").path(0).path("source").asText());
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM kt_prediction_results WHERE user_id=? AND operation_id='op049'", Integer.class, USER));
  }
}
