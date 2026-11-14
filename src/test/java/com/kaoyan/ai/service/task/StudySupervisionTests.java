package com.kaoyan.ai.service.task;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.KaoyanAiApplication;
import com.kaoyan.ai.persistence.entity.UserAccount;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.kaoyan.ai.persistence.repository.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = KaoyanAiApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:study_supervision;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.seed-local=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class StudySupervisionTests {
  @Autowired ReminderService scheduler;
  @Autowired MockMvc mvc;
  @Autowired UserAccountRepository users;
  @Autowired RecordRepository db;
  @Autowired StudySupervisionService study;

  private static final String PASSWORD = "StudyMode2026!";
  private String user;
  private String planId;
  private String taskId;
  private Instant at;

  @BeforeEach void setup() {
    user = account();
    planId = id();
    taskId = id();
    at = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(900);
    db.create("profiles", user, user, obj("activePlanId", planId, "version", 1));
    db.create("plans", planId, user, obj("id", planId, "status", "active", "version", 1,
        "targetDate", today().plusDays(10).toString(), "tasks", arr(
            obj("id", taskId, "content", "英语长难句", "status", "pending", "minutes", 20,
                "scheduledDate", today().toString(), "completedAt", null, "version", 1))));
  }

  @Autowired com.kaoyan.ai.persistence.mapper.IdentityMapper identities;

  private String account() {
    String id = id();
    users.create(new UserAccount(id, "s_" + id.substring(0, 8), "督学验证",
        new BCryptPasswordEncoder(4).encode(PASSWORD)));
    identities.markDevelopmentAccount(id, "s_" + id.substring(0, 8) + "@example.test");
    return id;
  }

  private record Client(MockHttpSession session, Cookie cookie, String csrf) {}

  private Client login(String owner, String ip) throws Exception {
    var token = mvc.perform(get("/api/v1/session/csrf").with(r -> { r.setRemoteAddr(ip); return r; }))
        .andReturn();
    var session = (MockHttpSession) token.getRequest().getSession(false);
    String csrf = data(token.getResponse().getContentAsString()).path("token").asText();
    var result = mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-Token", csrf)
        .with(r -> { r.setRemoteAddr(ip); return r; }).contentType(MediaType.APPLICATION_JSON)
        .content(obj("email", "s_" + owner.substring(0, 8) + "@example.test", "password", PASSWORD).toString()))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    var loginSession = (MockHttpSession) result.getRequest().getSession(false);
    Cookie cookie = result.getResponse().getCookie("AUTH_TOKEN");
    var fresh = mvc.perform(get("/api/v1/session/csrf").session(loginSession).cookie(cookie)).andReturn();
    return new Client(loginSession, cookie, data(fresh.getResponse().getContentAsString()).path("token").asText());
  }

  private JsonNode data(String raw) { return parse(raw).path("data"); }

  private JsonNode getMode(Client c) throws Exception {
    var response = mvc.perform(get("/api/v1/study-mode").session(c.session()).cookie(c.cookie())).andReturn().getResponse();
    assertEquals(200, response.getStatus());
    return data(response.getContentAsString());
  }

  private JsonNode putMode(Client c, String mode, int status) throws Exception {
    var response = mvc.perform(put("/api/v1/study-mode").session(c.session()).cookie(c.cookie())
        .header("X-CSRF-Token", c.csrf()).contentType(MediaType.APPLICATION_JSON).content(obj("mode", mode).toString()))
        .andReturn().getResponse();
    assertEquals(status, response.getStatus(), response.getContentAsString());
    return data(response.getContentAsString());
  }

  @Test void settingsPersistAcrossLoginsAndAreOwned() throws Exception {
    String other = account();
    var student = login(user, "198.18.1.1");
    assertEquals("GENTLE", getMode(student).path("mode").asText());
    for (String mode : new String[] {"SCHOLAR", "GENTLE", "STRICT"})
      assertEquals(mode, putMode(student, mode, 200).path("mode").asText());
    putMode(student, "UNKNOWN", 400);
    student = login(user, "198.18.1.2");
    assertEquals("STRICT", getMode(student).path("mode").asText());
    var otherStudent = login(other, "198.18.1.3");
    assertEquals("GENTLE", getMode(otherStudent).path("mode").asText());
    var legacy = mvc.perform(get("/api/v1/avatar/preferences").session(student.session()).cookie(student.cookie()))
        .andReturn().getResponse();
    assertEquals(200, legacy.getStatus());
    assertEquals("strict", data(legacy.getContentAsString()).path("mode").asText());
    assertEquals(401, mvc.perform(get("/api/v1/study-mode")).andReturn().getResponse().getStatus());

    var schedule = obj("action", "SCHEDULE", "scheduledAt", at.toString()).toString();
    for (var client : new Client[] {otherStudent, student}) {
      var updated = mvc.perform(put("/api/v1/study-supervision/tasks/{taskId}", taskId)
          .session(client.session()).cookie(client.cookie()).header("X-CSRF-Token", client.csrf())
          .contentType(MediaType.APPLICATION_JSON).content(schedule)).andReturn().getResponse();
      assertEquals(client == student ? 200 : 404, updated.getStatus(), updated.getContentAsString());
    }
    scheduler.remind();
    var state = mvc.perform(get("/api/v1/study-supervision").session(student.session()).cookie(student.cookie())
        .param("date", today().toString())).andReturn().getResponse();
    assertEquals(200, state.getStatus(), state.getContentAsString());
    assertEquals(taskId, data(state.getContentAsString()).path("reminders").get(0).path("taskId").asText());
  }

  private void schedule(Instant when) {
    study.act(user, taskId, obj("action", "SCHEDULE", "scheduledAt", when.toString()));
  }

  private void scan(Instant now) {
    db.lockOwner(user);
    study.scan(user, db.get("plans", planId, user), now);
  }

  private int sent() { return db.list("notifications", user).size(); }
  private ObjectNode state() { return study.state(user, today()); }

  @Test void scheduledJobSkipsDateOnlyAndCancelledTasksAndRecommendsRealPriority() {
    db.create("reminder_preferences", user, user, obj("enabled", true, "time", "00:00", "mode", "strict"));
    var plan = db.get("plans", planId, user);
    ((ObjectNode) plan.path("tasks").get(0)).put("priority", 1);
    String recommended = id();
    String cancelled = id();
    plan.withArray("tasks").add(obj("id", recommended, "content", "高优先级阅读", "priority", 2,
        "status", "pending", "minutes", 30, "version", 1, "scheduledDate", today().toString()));
    plan.withArray("tasks").add(obj("id", cancelled, "content", "取消任务", "priority", 3,
        "status", "pending", "minutes", 30, "version", 1, "scheduledDate", today().toString()));
    plan.put("version", 2);
    db.update("plans", planId, user, plan, 1);
    study.act(user, cancelled, obj("action", "SCHEDULE", "scheduledAt", at.toString()));
    plan = db.get("plans", planId, user);
    ((ObjectNode) plan.path("tasks").get(2)).put("status", "cancelled");
    int planVersion = plan.path("version").asInt();
    plan.put("version", planVersion + 1);
    db.update("plans", planId, user, plan, planVersion);

    study.saveMode(user, "SCHOLAR");
    scheduler.remind();
    assertEquals(0, sent());
    schedule(at);
    scheduler.remind();
    scheduler.remind();
    assertEquals(1, sent());
    var card = state().path("reminders").get(0);
    assertEquals(recommended, card.path("taskId").asText());
    assertEquals(taskId, card.path("sourceTaskId").asText());
    assertEquals(30, card.path("minutes").asInt());
    study.act(user, recommended, obj("action", "START"));
    assertEquals(recommended, state().path("timers").get(0).path("id").asText());
    study.act(user, recommended, obj("action", "COMPLETE"));
    study.saveMode(user, "STRICT");
    assertTrue(state().path("feedback").path("text").asText().contains("待办：英语长难句"));
  }

  @Test void modeThresholdsAndDedupSurviveReschedulingAndModeChanges() {
    schedule(at);
    scan(at.plusSeconds(599));
    assertEquals(0, sent());
    scan(at.plusSeconds(600));
    scan(at.plusSeconds(601));
    assertEquals(1, sent());
    assertEquals("START_FIVE", state().path("reminders").get(0).path("action").asText());
    study.saveMode(user, "STRICT");
    scan(at.plusSeconds(700));
    assertEquals(1, sent(), "首次已在第 5 分钟后发出，不补第二条");

    Instant changed = at.plusSeconds(1800);
    schedule(changed);
    scan(changed.minusSeconds(1));
    assertEquals(1, sent());
    scan(changed);
    scan(changed.plusSeconds(299));
    assertEquals(2, sent());
    scan(changed.plusSeconds(300));
    scan(changed.plusSeconds(700));
    assertEquals(3, sent());
    assertEquals(2, state().path("reminders").get(0).path("round").asInt());
    study.act(user, taskId, obj("action", "DISMISS", "scheduledAt", changed.toString(), "round", 2));
    assertTrue(state().path("reminders").isEmpty());

    Instant third = changed.plusSeconds(1800);
    schedule(third);
    study.saveMode(user, "SCHOLAR");
    scan(third.plusSeconds(299));
    assertEquals(3, sent());
    scan(third.plusSeconds(300));
    assertEquals(4, sent());
    study.saveMode(user, "STRICT");
    scan(third.plusSeconds(301));
    assertEquals(4, sent(), "第 5 分钟发出的首次提醒也不追加");
  }

  @Test void strictStartStopsLaterRemindersAndModeChangesKeepTimer() {
    study.saveMode(user, "STRICT");
    schedule(at);
    scan(at);
    var card = state().path("reminders").get(0);
    assertEquals(taskId, card.path("taskId").asText());
    study.act(user, card.path("taskId").asText(), obj("action", "START"));
    var timer = state().path("timers").get(0);
    assertEquals(1200, Duration.between(Instant.parse(timer.path("focusStart").asText()),
        Instant.parse(timer.path("focusEnd").asText())).toSeconds());
    scan(at.plusSeconds(300));
    assertEquals(1, sent());
    study.saveMode(user, "GENTLE");
    assertEquals(timer, state().path("timers").get(0));
    assertTrue(state().path("reminders").isEmpty());
  }

  @Test void fiveMinutesExpireWithoutCompletingAndCompletionUsesPlan() {
    schedule(at);
    scan(at.plusSeconds(600));
    study.act(user, taskId, obj("action", "START"));
    var timer = state().path("timers").get(0);
    assertEquals(300, Duration.between(Instant.parse(timer.path("focusStart").asText()),
        Instant.parse(timer.path("focusEnd").asText())).toSeconds());
    var metadata = db.get("study_supervision", "task:" + taskId, user);
    metadata.put("focusStart", at.toString());
    metadata.put("focusEnd", at.plusSeconds(300).toString());
    int version = metadata.path("version").asInt();
    metadata.put("version", version + 1);
    db.update("study_supervision", "task:" + taskId, user, metadata, version);

    assertEquals("pending", db.get("plans", planId, user).path("tasks").get(0).path("status").asText());
    study.act(user, taskId, obj("action", "STOP"));
    assertTrue(state().path("timers").get(0).path("focusEnd").isNull());
    assertEquals(5.0, db.list("study_events", user).get(0).path("effectiveMinutes").asDouble());
    study.act(user, taskId, obj("action", "CONTINUE"));
    study.act(user, taskId, obj("action", "COMPLETE"));
    study.saveMode(user, "SCHOLAR");
    assertTrue(state().path("feedback").path("text").asText().contains("实际学习 5 分钟"));
    assertTrue(state().path("timers").isEmpty());
    scan(at.plusSeconds(900));
    assertEquals(1, sent());
  }
}
