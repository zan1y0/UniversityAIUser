package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static com.kaoyan.ai.persistence.LocalDataInitializer.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.contract.ContractRegistry;
import com.kaoyan.ai.persistence.RecordRepository;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:flows;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.local-student-password=LocalStudy2026!",
      "app.seed-local=true",
      "app.files-directory=./target/test-private-files"
    })
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.context.TestExecutionListeners(
    listeners = {
      org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
      org.springframework.test.context.web.ServletTestExecutionListener.class
    },
    mergeMode = org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class BackendContractTests {
  @BeforeEach
  void before(TestInfo info) {
    System.out.println("业务验证开始：" + info.getDisplayName());
  }

  @AfterEach
  void after(TestInfo info) {
    System.out.println("业务验证结束：" + info.getDisplayName());
  }

  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired com.kaoyan.ai.jobs.ExamDeadlineService deadlineService;
  @Autowired MockMvc mvc;
  @Autowired ContractRegistry contract;
  @Autowired RecordRepository db;
  static final String PREFIX = "/api/v1";
  static final String KID = "40000000-0000-4000-8000-000000000001";
  static final String PV = "61000000-0000-4000-8000-000000000001";
  static final ArrayNode LOG = arr();

  private static class Client {
    MockHttpSession session;
    String csrf;
    String login;
  }

  Client login(String name) throws Exception {
    Client c = new Client();
    c.login = name;
    call(c, "GET", "/session/csrf", null, null, 200);
    JsonNode user =
        call(
            c,
            "POST",
            "/session/login",
            obj("loginName", name, "password", "LocalStudy2026!"),
            null,
            200);
    assertEquals(name, user.path("loginName").asText());
    call(c, "GET", "/session/csrf", null, null, 200);
    return c;
  }

  JsonNode call(Client c, String method, String path, JsonNode body, String key, int expected)
      throws Exception {
    return call(c, method, path, body, key, expected, true);
  }

  JsonNode call(
      Client c, String method, String path, JsonNode body, String key, int expected, boolean csrf)
      throws Exception {
    MockHttpServletRequestBuilder b =
        request(HttpMethod.valueOf(method), java.net.URI.create(PREFIX + path));
    if (c.session != null) b.session(c.session);
    if (csrf && c.csrf != null) b.header("X-CSRF-Token", c.csrf);
    if (key != null) b.header("Idempotency-Key", key);
    if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body.toString());
    MvcResult result = mvc.perform(b).andReturn();
    c.session = (MockHttpSession) result.getRequest().getSession(false);
    String raw = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    synchronized (LOG) {
      LOG.add(
          obj(
              "method",
              method,
              "path",
              path,
              "body",
              body,
              "expectedStatus",
              expected,
              "actualStatus",
              result.getResponse().getStatus()));
    }
    assertEquals(expected, result.getResponse().getStatus(), method + " " + path + " " + raw);
    if (expected == 204) {
      assertEquals("", raw);
      return com.fasterxml.jackson.databind.node.NullNode.instance;
    }
    JsonNode n = parse(raw);
    assertTrue(n.path("traceId").isTextual());
    assertFalse(raw.contains("password_hash"));
    if (expected < 300) {
      assertEquals("OK", n.path("code").asText());
      assertFalse(n.path("data").isNull());
      if (path.startsWith("/session/csrf")) c.csrf = n.path("data").path("token").asText();
      return n.get("data");
    }
    assertTrue(n.path("data").isNull());
    return n;
  }

  JsonNode write(Client c, String path, JsonNode body, int status) throws Exception {
    String key =
        body.has("requestId")
            ? body.path("requestId").asText()
            : body.has("eventId") ? body.path("eventId").asText() : id();
    return call(c, "POST", path, body, key, status);
  }

  JsonNode job(Client c, JsonNode accepted, boolean success) throws Exception {
    String jid = accepted.path("jobId").asText();
    long until = System.currentTimeMillis() + 12000;
    JsonNode current = null;
    while (System.currentTimeMillis() < until) {
      current = call(c, "GET", "/jobs/" + jid, null, null, 200);
      String state = current.path("status").asText();
      if (Set.of("completed", "failed", "failed_retryable", "cancelled").contains(state)) {
        if (success) assertEquals("completed", state, current.toString());
        else {
          assertEquals("failed_retryable", state, current.toString());
          assertEquals("DEPENDENCY_UNAVAILABLE", current.path("error").path("code").asText());
        }
        return current;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("任务未在测试期限完成：" + current);
  }

  JsonNode newPractice(Client c) throws Exception {
    return write(
        c,
        "/practice/sessions",
        obj("type", "daily", "subjectId", MATH, "knowledgeId", null, "difficulty", "medium"),
        201);
  }

  JsonNode newPlan(Client c) throws Exception {
    JsonNode p = call(c, "GET", "/profile", null, null, 200);
    JsonNode accepted =
        write(
            c,
            "/plans",
            obj(
                "profileVersion",
                p.get("version"),
                "targetDate",
                today().plusDays(14).toString(),
                "dailyMinutes",
                120,
                "baseline",
                arr(obj("subjectId", MATH, "score", 60, "maxScore", 150))),
            202);
    return job(c, accepted, true).path("result").path("plan");
  }

  @Test
  @Order(1)
  void loginCsrfAndOwnership() throws Exception {
    Client c = new Client();
    call(c, "GET", "/profile", null, null, 401);
    call(c, "GET", "/session/csrf", null, null, 200);
    call(
        c,
        "POST",
        "/session/login",
        obj("loginName", "student", "password", "LocalStudy2026!"),
        null,
        403,
        false);
    call(
        c,
        "POST",
        "/session/login",
        obj("loginName", "student", "password", "wrongpassword"),
        null,
        401);
    String token = c.csrf;
    call(
        c,
        "POST",
        "/session/login",
        obj("loginName", "student", "password", "LocalStudy2026!"),
        null,
        200);
    call(c, "POST", "/session/logout", null, null, 403);
    call(c, "GET", "/session/csrf", null, null, 200);
    assertNotEquals(token, c.csrf);
    JsonNode profile = call(c, "GET", "/profile", null, null, 200);
    assertEquals(USER, profile.path("user").path("id").asText());
    JsonNode practice = newPractice(c);
    Client other = login("student2");
    call(other, "GET", "/practice/sessions/" + practice.path("id").asText(), null, null, 404);
    call(c, "POST", "/session/logout", null, null, 204);
    c.session = null;
    c.csrf = null;
    call(c, "GET", "/profile", null, null, 401);
  }

  @Test
  @Order(2)
  void contractStrictValidationAndVersions() throws Exception {
    Client c = login("student");
    JsonNode p = call(c, "GET", "/profile", null, null, 200);
    ObjectNode body =
        obj(
            "undergraduateMajor",
            "软件工程",
            "targetProgramId",
            PROGRAM,
            "targetDate",
            today().plusDays(60).toString(),
            "dailyMinutes",
            120,
            "timezone",
            "Asia/Shanghai",
            "baseline",
            arr(),
            "version",
            p.get("version"));
    JsonNode saved = call(c, "PUT", "/profile", body, null, 200);
    assertEquals(p.path("version").asInt() + 1, saved.path("version").asInt());
    call(c, "PUT", "/profile", body, null, 409);
    body.put("userId", USER);
    call(c, "PUT", "/profile", body, null, 400);
    call(c, "GET", "/schools/programs?year=2027&userId=" + USER, null, null, 400);
    call(c, "GET", "/practice/sessions/not-a-uuid", null, null, 400);
    call(
        c,
        "POST",
        "/practice/sessions",
        obj("type", "special", "subjectId", MATH, "knowledgeId", null, "difficulty", "medium"),
        id(),
        400);
    call(
        c,
        "POST",
        "/interviews",
        obj(
            "programId",
            PROGRAM,
            "mode",
            "english",
            "language",
            "zh-CN",
            "duration",
            300,
            "consent",
            true),
        id(),
        400);
    call(
        c,
        "GET",
        "/analytics/dashboard?from=2026-02-30&to=2026-03-01&timezone=Asia%2FShanghai",
        null,
        null,
        400);
  }

  @Test
  @Order(3)
  void catalogsSchoolsAndNoInventedProbability() throws Exception {
    Client c = login("student");
    JsonNode list =
        call(c, "GET", "/schools/programs?year=2027&page=1&pageSize=10", null, null, 200);
    assertEquals(2, list.path("total").asInt());
    call(
        c,
        "POST",
        "/schools/recommendations",
        obj(
            "undergraduateMajor",
            "软件工程",
            "regions",
            arr("北京"),
            "year",
            2027,
            "estimatedScore",
            350,
            "difficulty",
            "medium"),
        null,
        200);
    call(
        c,
        "GET",
        "/schools/programs/" + PROGRAM + "/facts?year=2027&metricTypes=scoreLine,enrollment",
        null,
        null,
        200);
    call(
        c,
        "POST",
        "/schools/comparisons",
        obj("programIds", arr(PROGRAM, PROGRAM2), "year", 2027),
        null,
        200);
    JsonNode p = call(c, "GET", "/profile", null, null, 200);
    JsonNode j =
        job(
            c,
            write(
                c,
                "/schools/evaluations",
                obj(
                    "programId",
                    PROGRAM,
                    "estimatedScore",
                    350,
                    "profileVersion",
                    p.get("version")),
                202),
            true);
    JsonNode evaluation = j.get("result");
    assertEquals("insufficient_data", evaluation.path("status").asText());
    assertTrue(evaluation.path("probability").isNull());
    call(c, "GET", "/schools/evaluations/" + evaluation.path("id").asText(), null, null, 200);
  }

  @Test
  @Order(4)
  void publicProfessionalCoursesAndUnconfiguredModels() throws Exception {
    Client c = login("student");
    JsonNode tree =
        call(
            c,
            "GET",
            "/public-courses/knowledge?subject=math&examType=math1&year=2027",
            null,
            null,
            200);
    contract.model("KnowledgeTree", tree);
    call(c, "GET", "/public-courses/exam-points?subject=math&type=highFrequency", null, null, 200);
    job(
        c,
        write(
            c,
            "/public-courses/explanations",
            obj("knowledgeId", KID, "level", "basic", "version", 1),
            202),
        false);
    job(
        c,
        write(
            c,
            "/public-courses/english/tasks",
            obj("type", "composition", "content", "I enjoy studying computer science."),
            202),
        false);
    job(
        c,
        write(c, "/public-courses/politics/tasks", obj("type", "syllabus", "year", 2027), 202),
        false);
    job(
        c,
        write(c, "/public-courses/math/tasks", obj("type", "guidance", "content", "如何求导？"), 202),
        false);
    call(
        c,
        "GET",
        "/professional-courses/subjects?discipline=computer&programId=" + PROGRAM + "&year=2027",
        null,
        null,
        200);
    call(
        c,
        "GET",
        "/professional-courses/knowledge?subjectId=" + PROFESSIONAL + "&bookEdition=local-v1",
        null,
        null,
        200);
    JsonNode questions =
        call(
            c,
            "GET",
            "/professional-courses/past-questions?subjectId=" + PROFESSIONAL + "&year=2026",
            null,
            null,
            200);
    assertFalse(questions.toString().contains("correctAnswer"));
    job(
        c,
        write(
            c,
            "/professional-courses/book-explanations",
            obj(
                "bookId",
                "70000000-0000-4000-8000-000000000001",
                "edition",
                "local-v1",
                "chapterId",
                "71000000-0000-4000-8000-000000000001",
                "question",
                "什么是顺序表？"),
            202),
        false);
    job(
        c,
        write(
            c,
            "/professional-courses/explanations",
            obj(
                "questionVersionId",
                questions.path("items").path(0).get("versionId"),
                "level",
                "basic"),
            202),
        false);
    job(
        c,
        write(
            c,
            "/professional-courses/predictions",
            obj(
                "subjectId",
                PROFESSIONAL,
                "targetYear",
                2027,
                "historyRange",
                obj("fromYear", 2020, "toYear", 2026)),
            202),
        true);
  }

  @Test
  @Order(5)
  void avatarModesCheckinAndReminders() throws Exception {
    Client c = login("student");
    call(c, "GET", "/avatar/assets", null, null, 200);
    JsonNode prefs = call(c, "GET", "/avatar/preferences", null, null, 200);
    ObjectNode change = (ObjectNode) prefs.deepCopy();
    change.put("mode", "strict");
    call(c, "PUT", "/avatar/preferences", change, null, 200);
    JsonNode reminder = call(c, "GET", "/avatar/reminders", null, null, 200);
    call(c, "PUT", "/avatar/reminders", reminder, null, 200);
    String key = id();
    JsonNode body = obj("localDate", today().toString());
    JsonNode a = call(c, "POST", "/avatar/checkins", body, key, 201);
    JsonNode b = call(c, "POST", "/avatar/checkins", body, key, 201);
    assertEquals(a, b);
    call(
        c, "POST", "/avatar/checkins", obj("localDate", today().minusDays(1).toString()), key, 409);
    call(c, "GET", "/avatar/supervision?date=" + today(), null, null, 200);
    job(c, write(c, "/avatar/interactions", obj("text", "我有点紧张", "contextId", id()), 202), false);
  }

  @Test
  @Order(6)
  void practiceGradingWrongItemsAndIdempotency() throws Exception {
    Client c = login("student");
    JsonNode session = newPractice(c);
    assertFalse(session.toString().contains("correctAnswer"));
    String sid = session.path("id").asText();
    call(c, "GET", "/practice/sessions/" + sid, null, null, 200);
    JsonNode q = session.path("questions").path(0);
    String key = id();
    ObjectNode body =
        obj(
            "questionVersionId",
            q.get("versionId"),
            "answer",
            obj("kind", "singleChoice", "optionId", "A"),
            "requestId",
            key);
    JsonNode a = call(c, "POST", "/practice/sessions/" + sid + "/attempts", body, key, 201);
    assertFalse(a.path("correct").asBoolean());
    assertEquals("B", a.path("correctAnswer").path("optionId").asText());
    assertEquals(a, call(c, "POST", "/practice/sessions/" + sid + "/attempts", body, key, 201));
    ((ObjectNode) body.get("answer")).put("optionId", "B");
    call(c, "POST", "/practice/sessions/" + sid + "/attempts", body, key, 409);
    body.put("requestId", id());
    call(
        c,
        "POST",
        "/practice/sessions/" + sid + "/attempts",
        body,
        body.path("requestId").asText(),
        201);
    JsonNode wrong = call(c, "GET", "/practice/wrong-items?subjectId=" + MATH, null, null, 200);
    assertTrue(wrong.path("total").asInt() > 0);
    call(
        c,
        "GET",
        "/practice/wrong-items/" + a.path("wrongItemId").asText() + "/history",
        null,
        null,
        200);
    call(
        c,
        "GET",
        "/practice/similar-questions?questionId="
            + q.path("id").asText()
            + "&knowledgeId="
            + KID
            + "&difficulty=medium",
        null,
        null,
        200);
    call(
        c,
        "GET",
        "/practice/knowledge-progress?subjectId=" + MATH + "&timeRange=all",
        null,
        null,
        200);
  }

  @Test
  @Order(7)
  void planDraftConfirmCompletionAdjustment() throws Exception {
    Client c = login("student");
    JsonNode p = newPlan(c);
    String pid = p.path("id").asText();
    assertEquals("draft", p.path("status").asText());
    JsonNode task = p.path("tasks").path(0);
    JsonNode patched =
        call(
            c,
            "PATCH",
            "/plans/" + pid + "/tasks/" + task.path("id").asText(),
            obj("content", "导数专项复习", "version", task.get("version")),
            null,
            200);
    JsonNode fresh =
        call(c, "GET", "/plans/" + pid + "?view=week&date=" + today(), null, null, 200);
    JsonNode active =
        write(c, "/plans/" + pid + "/confirm", obj("version", fresh.get("version")), 200);
    call(
        c,
        "PATCH",
        "/plans/" + pid + "/tasks/" + task.path("id").asText(),
        obj("content", "不能直接修改生效任务", "version", patched.get("version")),
        null,
        409);
    write(
        c,
        "/plans/" + pid + "/tasks/" + task.path("id").asText() + "/complete",
        obj("version", patched.get("version"), "eventId", id()),
        200);
    JsonNode progress =
        call(c, "GET", "/plans/" + pid + "/progress?timeRange=all", null, null, 200);
    assertEquals(1, progress.path("completedTasks").asInt());
    JsonNode adjustment =
        job(
            c,
            write(
                c,
                "/plans/" + pid + "/adjustments",
                obj(
                    "reason",
                    "调整时间预算",
                    "timeBudget",
                    90,
                    "progressVersion",
                    progress.get("version")),
                202),
            true);
    assertEquals("draft", adjustment.path("result").path("plan").path("status").asText());
    JsonNode profile = call(c, "GET", "/profile", null, null, 200);
    assertEquals(pid, profile.path("activePlanId").asText());
  }

  @Test
  @Order(8)
  void mockExamSaveResumeSubmitScoreRankAndEstimate() throws Exception {
    Client c = login("student");
    call(c, "GET", "/mock-exams/papers?subject=math&examType=math1&year=2027", null, null, 200);
    JsonNode s =
        write(c, "/mock-exams/sessions", obj("paperVersionId", PV, "requestId", id()), 201);
    String sid = s.path("id").asText();
    assertFalse(s.toString().contains("correctAnswer"));
    JsonNode q = s.path("questions").path(0);
    String url = "/mock-exams/sessions/" + sid + "/answers/" + q.path("id").asText();
    String key = id();
    ObjectNode body =
        obj("answer", obj("kind", "singleChoice", "optionId", "B"), "version", 0, "requestId", key);
    call(c, "PUT", url, body, key, 200);
    call(c, "PUT", url, body, key, 200);
    String badKey = id();
    ObjectNode bad = body.deepCopy();
    bad.put("requestId", badKey);
    call(c, "PUT", url, bad, badKey, 409);
    JsonNode resumed = call(c, "GET", "/mock-exams/sessions/" + sid, null, null, 200);
    assertEquals(1, resumed.path("answers").size());
    JsonNode submit =
        write(
            c,
            "/mock-exams/sessions/" + sid + "/submit",
            obj("version", resumed.get("version")),
            202);
    assertEquals(
        "submitted",
        call(c, "GET", "/mock-exams/sessions/" + sid, null, null, 200).path("status").asText());
    bad.put("version", 1);
    badKey = id();
    bad.put("requestId", badKey);
    call(c, "PUT", url, bad, badKey, 409);
    JsonNode scored = job(c, submit, true).get("result");
    assertEquals(5, scored.path("totalScore").asDouble());
    call(c, "GET", "/mock-exams/sessions/" + sid + "/result", null, null, 200);
    call(
        c,
        "GET",
        "/mock-exams/papers/60000000-0000-4000-8000-000000000001/ranking?paperVersionId=" + PV,
        null,
        null,
        200);
    JsonNode profile = call(c, "GET", "/profile", null, null, 200);
    job(
        c,
        write(
            c,
            "/mock-exams/estimates",
            obj(
                "scoreSnapshotIds",
                arr(scored.get("scoreSnapshotId")),
                "profileVersion",
                profile.get("version")),
            202),
        true);
  }

  @Test
  @Order(9)
  void qaVersionFailureCancellationAndFilePrivacy() throws Exception {
    Client c = login("student");
    byte[] bytes =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jwZ0AAAAASUVORK5CYII=");
    MockMultipartFile upload = new MockMultipartFile("file", "sample.png", "image/png", bytes);
    MvcResult uploaded =
        mvc.perform(
                multipart(PREFIX + "/files")
                    .file(upload)
                    .param("purpose", "qaImage")
                    .session(c.session)
                    .header("X-CSRF-Token", c.csrf))
            .andReturn();
    assertEquals(201, uploaded.getResponse().getStatus());
    JsonNode f = parse(uploaded.getResponse().getContentAsString()).get("data");
    String fid = f.path("id").asText();
    MvcResult downloaded =
        mvc.perform(get(PREFIX + "/files/" + fid).session(c.session)).andReturn();
    assertArrayEquals(bytes, downloaded.getResponse().getContentAsByteArray());
    Client other = login("student2");
    call(other, "GET", "/files/" + fid, null, null, 404);
    job(c, write(c, "/qa/image-recognitions", obj("fileId", fid, "subjectId", MATH), 202), false);
    JsonNode conv = write(c, "/qa/conversations", obj("subjectId", MATH), 201);
    String cid = conv.path("id").asText();
    JsonNode accepted =
        write(
            c,
            "/qa/conversations/" + cid + "/messages",
            obj("subjectId", MATH, "text", "如何理解导数？", "requestId", id(), "contextVersion", 1),
            202);
    job(c, accepted, false);
    call(c, "GET", "/qa/conversations/" + cid + "/messages", null, null, 200);
    call(c, "GET", "/qa/messages/" + accepted.path("messageId").asText(), null, null, 200);
    call(
        c,
        "POST",
        "/qa/conversations/" + cid + "/messages",
        obj("subjectId", MATH, "text", "继续讲", "requestId", id(), "contextVersion", 1),
        id(),
        422);
    accepted =
        write(
            c,
            "/qa/conversations/" + cid + "/messages",
            obj("subjectId", MATH, "text", "请继续举例", "requestId", id(), "contextVersion", 2),
            202);
    String mid = accepted.path("messageId").asText();
    JsonNode msg = call(c, "GET", "/qa/messages/" + mid, null, null, 200);
    if (Set.of("queued", "running").contains(msg.path("status").asText()))
      call(
          c,
          "POST",
          "/qa/messages/" + mid + "/cancel",
          obj("version", msg.get("version")),
          null,
          200);
    call(c, "DELETE", "/files/" + fid, null, null, 204);
  }

  @Test
  @Order(10)
  void interviewAndUnavailableScoring() throws Exception {
    Client c = login("student");
    JsonNode i =
        write(
            c,
            "/interviews",
            obj(
                "programId",
                PROGRAM,
                "mode",
                "english",
                "language",
                "en-US",
                "duration",
                300,
                "consent",
                true),
            201);
    assertEquals("en-US", i.path("language").asText());
    String iid = i.path("id").asText();
    job(
        c,
        write(
            c,
            "/interviews/" + iid + "/turns",
            obj("text", "I study software engineering.", "requestId", id()),
            202),
        false);
    job(
        c,
        write(c, "/interviews/" + iid + "/finish", obj("version", i.get("version")), 202),
        false);
    JsonNode report = call(c, "GET", "/interviews/" + iid + "/result", null, null, 200);
    assertEquals("failed_retryable", report.path("status").asText());
    assertTrue(report.path("summary").isNull());
    job(
        c,
        write(
            c,
            "/interviews/self-introduction-reviews",
            obj("content", "我学习软件工程。", "language", "zh-CN", "programId", PROGRAM),
            202),
        false);
    call(c, "GET", "/interviews/guidance?type=etiquette&programId=" + PROGRAM, null, null, 200);
  }

  @Test
  @Order(11)
  void analyticsOverlapWeeklyReportAndNoZeroSampleRate() throws Exception {
    Client c = login("student");
    Instant end = Instant.now().minusSeconds(60), start = end.minusSeconds(1800);
    String event = id();
    JsonNode body =
        obj(
            "eventId",
            event,
            "type",
            "reading",
            "startAt",
            start.toString(),
            "endAt",
            end.toString(),
            "taskId",
            null,
            "focusSelfReport",
            0.8);
    JsonNode first = write(c, "/analytics/study-events", body, 201);
    assertEquals(30, first.path("effectiveMinutes").asDouble(), 0.001);
    ObjectNode overlap = (ObjectNode) body.deepCopy();
    overlap.put("eventId", id());
    assertEquals(
        0, write(c, "/analytics/study-events", overlap, 201).path("effectiveMinutes").asDouble());
    JsonNode board =
        call(
            c,
            "GET",
            "/analytics/dashboard?from=" + today() + "&to=" + today() + "&timezone=Asia%2FShanghai",
            null,
            null,
            200);
    assertEquals(30, board.path("studyMinutes").asDouble(), 0.001);
    call(
        c,
        "GET",
        "/analytics/weakness-radar?subjectId=" + MATH + "&from=" + today() + "&to=" + today(),
        null,
        null,
        200);
    LocalDate monday = today().with(DayOfWeek.MONDAY);
    call(
        c,
        "GET",
        "/analytics/weekly-reports?weekStart=" + monday + "&timezone=Asia%2FShanghai",
        null,
        null,
        200);
    Client empty = login("student2");
    JsonNode blank =
        call(
            empty,
            "GET",
            "/analytics/dashboard?from=" + today() + "&to=" + today() + "&timezone=Asia%2FShanghai",
            null,
            null,
            200);
    assertTrue(blank.path("correctRate").isNull());
    assertTrue(blank.path("focus").isNull());
  }

  @Test
  @Order(12)
  void newsFollowVersionsNotificationAndEventStream() throws Exception {
    Client c = login("student");
    JsonNode notices = call(c, "GET", "/news/notices?year=2027", null, null, 200);
    String nid = notices.path("items").path(0).path("id").asText();
    call(c, "GET", "/news/notices/" + nid + "/changes?fromVersion=1&toVersion=1", null, null, 200);
    JsonNode follows = call(c, "GET", "/news/follows", null, null, 200);
    ObjectNode change = (ObjectNode) follows.deepCopy();
    change.set("programIds", arr(PROGRAM));
    call(c, "PUT", "/news/follows", change, null, 200);
    call(c, "PUT", "/news/follows", change, null, 409);
    JsonNode milestones = call(c, "GET", "/news/milestones?year=2027", null, null, 200);
    for (JsonNode m : milestones.path("items")) {
      assertFalse(m.path("verified").asBoolean());
      assertTrue(m.path("startsAt").isNull());
    }
    JsonNode notifications = call(c, "GET", "/news/notifications?unreadOnly=true", null, null, 200);
    assertTrue(notifications.path("total").asInt() > 0);
    JsonNode n = notifications.path("items").path(0);
    call(
        c,
        "POST",
        "/news/notifications/" + n.path("id").asText() + "/read",
        obj("version", n.get("version")),
        null,
        200);
    MvcResult result =
        mvc.perform(get(PREFIX + "/events").session(c.session).header("Last-Event-ID", "1"))
            .andReturn();
    assertTrue(result.getRequest().isAsyncStarted());
    assertEquals(200, result.getResponse().getStatus());
  }

  @Test
  @Order(13)
  void concurrentIdempotentCreationCreatesOnlyOneSession() throws Exception {
    Client a = login("student"), b = login("student");
    String key = id();
    JsonNode request =
        obj("type", "daily", "subjectId", MATH, "knowledgeId", null, "difficulty", "medium");
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch gate = new CountDownLatch(1);
    try {
      Future<JsonNode> one =
          executor.submit(
              () -> {
                gate.await();
                return call(a, "POST", "/practice/sessions", request, key, 201);
              });
      Future<JsonNode> two =
          executor.submit(
              () -> {
                gate.await();
                return call(b, "POST", "/practice/sessions", request, key, 201);
              });
      gate.countDown();
      assertEquals(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS));
      assertEquals(
          1,
          db.jdbc()
              .queryForObject(
                  "SELECT COUNT(*) FROM idempotency_records WHERE user_id=? AND"
                      + " operation_id='op043' AND idem_key=?",
                  Integer.class,
                  USER,
                  key));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @Order(14)
  void cancelledQaCannotPublishLateCompletedResult() throws Exception {
    Client c = login("student");
    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx -> {
              try {
                JsonNode conversation = write(c, "/qa/conversations", obj("subjectId", MATH), 201);
                JsonNode accepted =
                    write(
                        c,
                        "/qa/conversations/" + conversation.path("id").asText() + "/messages",
                        obj(
                            "subjectId",
                            MATH,
                            "text",
                            "请讲解导数",
                            "requestId",
                            id(),
                            "contextVersion",
                            1),
                        202);
                String message = accepted.path("messageId").asText();
                JsonNode cancelled =
                    call(
                        c,
                        "POST",
                        "/qa/messages/" + message + "/cancel",
                        obj("version", 1),
                        null,
                        200);
                assertEquals("cancelled", cancelled.path("status").asText());
                JsonNode j =
                    call(c, "GET", "/jobs/" + accepted.path("jobId").asText(), null, null, 200);
                assertEquals("cancelled", j.path("status").asText());
                assertTrue(j.path("result").isNull());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  @Order(15)
  void serverDeadlineClosesExamWithoutBrowser() throws Exception {
    Client c = login("student");
    JsonNode session =
        write(c, "/mock-exams/sessions", obj("paperVersionId", PV, "requestId", id()), 201);
    String sid = session.path("id").asText();
    ObjectNode stored = db.get("exam_sessions", sid, USER);
    stored.put("deadlineAt", Instant.now().minusSeconds(1).toString());
    db.replace("exam_sessions", sid, USER, stored);
    deadlineService.closeExpired();
    JsonNode expired = call(c, "GET", "/mock-exams/sessions/" + sid, null, null, 200);
    assertEquals("submitted", expired.path("status").asText());
    JsonNode jobData = db.get("exam_submissions", sid, USER);
    job(c, jobData, true);
  }

  @Test
  @Order(16)
  void multipartPurposeMagicAndSizeAreValidated() throws Exception {
    Client c = login("student");
    MockMultipartFile fake =
        new MockMultipartFile("file", "fake.png", "image/png", "not a png document".getBytes());
    MvcResult rejected =
        mvc.perform(
                multipart(PREFIX + "/files")
                    .file(fake)
                    .param("purpose", "qaImage")
                    .session(c.session)
                    .header("X-CSRF-Token", c.csrf))
            .andReturn();
    assertEquals(422, rejected.getResponse().getStatus());
    byte[] tooLarge = new byte[10485761];
    MockMultipartFile large = new MockMultipartFile("file", "large.png", "image/png", tooLarge);
    MvcResult over =
        mvc.perform(
                multipart(PREFIX + "/files")
                    .file(large)
                    .param("purpose", "qaImage")
                    .session(c.session)
                    .header("X-CSRF-Token", c.csrf))
            .andReturn();
    assertEquals(422, over.getResponse().getStatus());
  }

  @Test
  @Order(17)
  void wavDurationPurposeUploadAndDownload() throws Exception {
    Client c = login("student");
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    byte[] pcm = new byte[16000];
    javax.sound.sampled.AudioFormat format =
        new javax.sound.sampled.AudioFormat(8000, 16, 1, true, false);
    javax.sound.sampled.AudioInputStream stream =
        new javax.sound.sampled.AudioInputStream(
            new java.io.ByteArrayInputStream(pcm), format, 8000);
    javax.sound.sampled.AudioSystem.write(
        stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, bytes);
    MockMultipartFile file =
        new MockMultipartFile("file", "sample.wav", "audio/wav", bytes.toByteArray());
    MvcResult r =
        mvc.perform(
                multipart(PREFIX + "/files")
                    .file(file)
                    .param("purpose", "interviewAudio")
                    .session(c.session)
                    .header("X-CSRF-Token", c.csrf))
            .andReturn();
    assertEquals(201, r.getResponse().getStatus());
    JsonNode f = parse(r.getResponse().getContentAsString()).get("data");
    assertEquals(1, f.path("durationSeconds").asDouble(), 0.001);
    MvcResult download =
        mvc.perform(get(PREFIX + "/files/" + f.path("id").asText()).session(c.session)).andReturn();
    assertArrayEquals(bytes.toByteArray(), download.getResponse().getContentAsByteArray());
    call(c, "DELETE", "/files/" + f.path("id").asText(), null, null, 204);
  }

  @AfterAll
  static void report() throws Exception {
    Files.createDirectories(Path.of("target/verification"));
    Files.writeString(Path.of("target/verification/backend-http-cases.json"), LOG.toPrettyString());
  }
}
