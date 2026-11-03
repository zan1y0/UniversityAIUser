package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static com.kaoyan.ai.persistence.LocalDataInitializer.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.ai.ModelGateway;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.contract.ContractRegistry;
import com.kaoyan.ai.persistence.RecordRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;

/** 测试专用模型替身，验证成功分支与取消竞争；运行时绝不启用。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:provider-flows;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.local-student-password=LocalStudy2026!",
      "app.seed-local=true"
    })
@AutoConfigureMockMvc
@Import(BackendModelFlowTests.ProviderConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackendModelFlowTests {
  @TestConfiguration
  static class ProviderConfiguration {
    @Bean
    @Primary
    TestProvider provider(ContractRegistry contract) {
      return new TestProvider(contract);
    }
  }

  static class TestProvider extends ModelGateway {
    volatile boolean failScore = false;
    volatile CountDownLatch gate = new CountDownLatch(0), started = new CountDownLatch(0);

    TestProvider(ContractRegistry c) {
      super("", "", "", "", "", 1, c);
    }

    @Override
    public String version() {
      return "test-provider";
    }

    @Override
    public JsonNode withSchema(String schema, String prompt) {
      if (schema.equals("ScoreDimension")) {
        if (failScore) throw new ApiException(503, "DEPENDENCY_UNAVAILABLE", "测试评分服务不可用");
        return obj("name", "主观题", "score", 8, "maxScore", 10, "feedback", "概念清晰，补充条件分析。");
      }
      if (schema.equals("QaGuidance")) {
        started.countDown();
        try {
          if (!gate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("测试门闩超时");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
        return obj(
            "steps",
            arr("先观察函数变化率。"),
            "hints",
            arr("对比割线与切线。"),
            "method",
            "通过极限理解导数",
            "sources",
            arr(),
            "modelVersion",
            "test-provider");
      }
      throw new IllegalArgumentException(schema);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired TestProvider provider;
  @Autowired RecordRepository db;
  MockHttpSession session;
  String csrf;

  JsonNode call(String method, String path, JsonNode data, String key, int status)
      throws Exception {
    var b = request(HttpMethod.valueOf(method), java.net.URI.create("/api/v1" + path));
    if (session != null) b.session(session);
    if (csrf != null) b.header("X-CSRF-Token", csrf);
    if (key != null) b.header("Idempotency-Key", key);
    if (data != null) b.contentType(MediaType.APPLICATION_JSON).content(data.toString());
    MvcResult r = mvc.perform(b).andReturn();
    session = (MockHttpSession) r.getRequest().getSession(false);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return parse(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
        .get("data");
  }

  @BeforeEach
  void login() throws Exception {
    csrf = call("GET", "/session/csrf", null, null, 200).path("token").asText();
    call(
        "POST",
        "/session/login",
        obj("loginName", "student", "password", "LocalStudy2026!"),
        null,
        200);
    csrf = call("GET", "/session/csrf", null, null, 200).path("token").asText();
    provider.failScore = false;
    provider.gate = new CountDownLatch(0);
    provider.started = new CountDownLatch(0);
  }

  JsonNode post(String path, JsonNode body, int status) throws Exception {
    String key = body.has("requestId") ? body.path("requestId").asText() : id();
    return call("POST", path, body, key, status);
  }

  JsonNode terminal(String job) throws Exception {
    for (int i = 0; i < 80; i++) {
      JsonNode j = call("GET", "/jobs/" + job, null, null, 200);
      if (Set.of("completed", "failed_retryable", "failed", "cancelled")
          .contains(j.path("status").asText())) return j;
      Thread.sleep(50);
    }
    throw new AssertionError("任务超时");
  }

  String mixedPaper() {
    String qid = id(), qv = id(), pid = id(), pv = id();
    ObjectNode q =
        obj(
            "id",
            qid,
            "versionId",
            qv,
            "subjectId",
            MATH,
            "knowledgeId",
            "40000000-0000-4000-8000-000000000001",
            "kind",
            "text",
            "stem",
            "解释导数的含义。",
            "options",
            arr(),
            "difficulty",
            "medium",
            "maxScore",
            10,
            "sources",
            arr());
    db.create(
        "questions",
        qv,
        "public",
        obj(
            "question",
            q,
            "correctAnswer",
            obj("kind", "text", "content", "导数表示局部变化率。"),
            "explanation",
            obj(),
            "year",
            2026));
    JsonNode paper =
        obj(
            "id",
            pid,
            "versionId",
            pv,
            "title",
            "测试混合题型试卷",
            "subject",
            "math",
            "examType",
            "math1",
            "year",
            2027,
            "durationMinutes",
            30,
            "maxScore",
            15,
            "questionCount",
            2,
            "sources",
            arr());
    db.create(
        "papers",
        pv,
        "public",
        obj("paper", paper, "questionVersionIds", arr("51000000-0000-4000-8000-000000000001", qv)));
    return pv;
  }

  JsonNode submitMixedExam() throws Exception {
    String paper = mixedPaper();
    JsonNode s = post("/mock-exams/sessions", obj("paperVersionId", paper, "requestId", id()), 201);
    String sid = s.path("id").asText();
    for (JsonNode q : s.path("questions")) {
      String key = id();
      JsonNode answer =
          q.path("kind").asText().equals("text")
              ? obj("kind", "text", "content", "导数是局部瞬时变化率。")
              : obj("kind", "singleChoice", "optionId", "B");
      call(
          "PUT",
          "/mock-exams/sessions/" + sid + "/answers/" + q.path("id").asText(),
          obj("answer", answer, "version", 0, "requestId", key),
          key,
          200);
    }
    s = call("GET", "/mock-exams/sessions/" + sid, null, null, 200);
    JsonNode accepted =
        post("/mock-exams/sessions/" + sid + "/submit", obj("version", s.get("version")), 202);
    return obj("sessionId", sid, "job", terminal(accepted.path("jobId").asText()));
  }

  @Test
  @Order(1)
  void successfulSubjectiveGradingAndScoreSnapshot() throws Exception {
    JsonNode completed = submitMixedExam().path("job");
    assertEquals("completed", completed.path("status").asText());
    JsonNode r = completed.get("result");
    assertEquals(5, r.path("objectiveScore").asDouble());
    assertEquals(8, r.path("subjectiveEstimate").asDouble());
    assertEquals(13, r.path("totalScore").asDouble());
    assertEquals("test-provider", r.path("modelVersion").asText());
    assertTrue(r.path("scoreSnapshotId").isTextual());
  }

  @Test
  @Order(2)
  void failedSubjectiveGradingRetainsObjectiveButNoTotal() throws Exception {
    provider.failScore = true;
    JsonNode submission = submitMixedExam();
    assertEquals("failed_retryable", submission.path("job").path("status").asText());
    JsonNode r =
        call(
            "GET",
            "/mock-exams/sessions/" + submission.path("sessionId").asText() + "/result",
            null,
            null,
            200);
    assertEquals(5, r.path("objectiveScore").asDouble());
    assertTrue(r.path("totalScore").isNull());
    assertTrue(r.path("scoreSnapshotId").isNull());
  }

  @Test
  @Order(3)
  void cancelDuringProviderCallPreventsLateResult() throws Exception {
    provider.gate = new CountDownLatch(1);
    provider.started = new CountDownLatch(1);
    JsonNode c = post("/qa/conversations", obj("subjectId", MATH), 201);
    JsonNode a =
        post(
            "/qa/conversations/" + c.path("id").asText() + "/messages",
            obj("subjectId", MATH, "text", "如何理解导数？", "requestId", id(), "contextVersion", 1),
            202);
    assertTrue(provider.started.await(3, TimeUnit.SECONDS));
    String mid = a.path("messageId").asText();
    JsonNode m = call("GET", "/qa/messages/" + mid, null, null, 200);
    long start = System.currentTimeMillis();
    call("POST", "/qa/messages/" + mid + "/cancel", obj("version", m.get("version")), null, 200);
    assertTrue(System.currentTimeMillis() - start < 2000, "取消不应等待模型结束");
    provider.gate.countDown();
    Thread.sleep(100);
    JsonNode job = terminal(a.path("jobId").asText());
    assertEquals("cancelled", job.path("status").asText());
    assertTrue(job.path("result").isNull());
    m = call("GET", "/qa/messages/" + mid, null, null, 200);
    assertEquals("cancelled", m.path("status").asText());
    assertTrue(m.path("guidance").isNull());
    JsonNode next =
        post(
            "/qa/conversations/" + c.path("id").asText() + "/messages",
            obj("subjectId", MATH, "text", "继续讲解", "requestId", id(), "contextVersion", 2),
            202);
    assertEquals("completed", terminal(next.path("jobId").asText()).path("status").asText());
  }
}
