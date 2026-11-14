package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.persistence.mapper.IdentityMapper;
import com.kaoyan.ai.service.auth.AccountMail;
import com.kaoyan.ai.service.auth.AdminAccounts;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:email-security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.seed-local=false"})
@AutoConfigureMockMvc
class EmailSecurityTests {
  private static final String PASSWORD = "Test2026Strong!";
  private static final String REPLACEMENT = "Test2027Changed!";
  private static final String ADMIN = "admin@example.test";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired IdentityMapper users;
  @Autowired AdminAccounts admin;
  @MockBean AccountMail mail;
  private final ConcurrentHashMap<String,String> codes = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String,String> links = new ConcurrentHashMap<>();

  @BeforeEach void setup() throws Exception {
    doAnswer(call -> {
      String email = call.getArgument(0);
      String content = call.getArgument(2);
      extract("[0-9]{6}(?=，)", content, codes, email);
      extract("(?<=#token=)[A-Za-z0-9_-]{43}", content, links, email);
      return null;
    }).when(mail).send(anyString(), anyString(), anyString());
    if (!users.bootstrapCompleted()) {
      admin.bootstrap(ADMIN, PASSWORD, "管理员");
      Client client = new Client();
      client.csrf();
      client.call("POST", "/auth/login", obj("email",ADMIN,"password",PASSWORD),401);
      jdbc.update("DELETE FROM email_send_quota");
      String token = links.get(ADMIN);
      assertNotNull(token);
      assertTrue(client.call("POST","/auth/invitation/check",obj("token",token),200).path("bootstrap").asBoolean());
      client.call("POST","/auth/email/code",obj("purpose","ACTIVATE","token",token),202);
      client.call("POST","/auth/invitation/activate",
          obj("token",token,"code",awaitCode(ADMIN),"password",PASSWORD,"confirmPassword",PASSWORD,"legacyPassword",PASSWORD),204);
      assertTrue(users.bootstrapCompleted());
    }
  }

  private void extract(String pattern, String value, ConcurrentHashMap<String,String> into, String email) {
    var matcher = Pattern.compile(pattern).matcher(value);
    if (matcher.find()) into.put(email,matcher.group());
  }

  private final class Client {
    final String source = UUID.randomUUID().toString();
    MockHttpSession session;
    Cookie cookie;
    String csrf;
    JsonNode call(String method, String route, JsonNode body, int expected) throws Exception {
      var result = perform(method, route, body);
      int status = result.getResponse().getStatus();
      String response = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
      assertEquals(expected,status,method + " " + route + " " + response);
      return response.isEmpty() ? null : parse(response).path("data");
    }
    org.springframework.test.web.servlet.MvcResult perform(String method, String route, JsonNode body) throws Exception {
      var request = request(HttpMethod.valueOf(method),"/api/v1" + route)
          .with(r -> { r.setRemoteAddr(source); return r; });
      if (session != null) request.session(session);
      if (cookie != null) request.cookie(cookie);
      if (csrf != null) request.header("X-CSRF-Token", csrf);
      if (body != null) request.contentType("application/json").content(body.toString());
      var result = mvc.perform(request).andReturn();
      session = (MockHttpSession) result.getRequest().getSession(false);
      var updated = result.getResponse().getCookie("AUTH_TOKEN");
      if (updated != null) cookie = updated.getMaxAge() == 0 ? null : updated;
      return result;
    }
    void csrf() throws Exception { csrf = call("GET","/session/csrf",null,200).path("token").asText(); }
    void login(String email, String password, boolean legacy) throws Exception {
      csrf();
      call("POST",legacy ? "/session/login" : "/auth/login",obj("email",email,"password",password),200);
      csrf();
    }
  }

  private String mailbox() { return "test-" + id() + "@example.test"; }

  private JsonNode registration(String email, String code) {
    return obj("email",email,"code",code,"password",PASSWORD,"confirmPassword",PASSWORD,
        "displayName","学生","acceptedTerms",true);
  }

  private Client student(String email) throws Exception {
    var client = new Client();
    client.csrf();
    client.call("POST","/auth/email/code",obj("purpose","REGISTER","email",email),202);
    client.call("POST","/auth/register",registration(email,codes.get(email)),201);
    client.csrf();
    return client;
  }

  @Test void registersOnlyStudentsAndRejectsDuplicateConcurrentSubmission() throws Exception {
    String email = mailbox();
    var first = new Client(); var second = new Client();
    first.csrf(); second.csrf();
    first.call("POST","/auth/register",registration(email,"000000"),422);
    first.call("POST","/auth/email/code",obj("purpose","REGISTER","email",email),202);
    second.call("POST","/auth/email/code",obj("purpose","REGISTER","email",email),429);
    var body = registration(email,codes.get(email));
    for (String role : new String[]{"TEACHER","ADMIN"}) {
      var forged = (com.fasterxml.jackson.databind.node.ObjectNode) body.deepCopy();
      forged.put("role",role);
      first.call("POST","/auth/register",forged,422);
    }
    try (var threads = new ClosingExecutor()) {
      var a = threads.executor.submit(() -> first.perform("POST","/auth/register",body).getResponse().getStatus());
      var b = threads.executor.submit(() -> second.perform("POST","/auth/register",body).getResponse().getStatus());
      var statuses = java.util.stream.Stream.of(a.get(),b.get()).sorted().toList();
      assertEquals(201,statuses.get(0));
      assertTrue(java.util.List.of(409,422).contains(statuses.get(1)));
    }
    var login = new Client();
    login.login(email.toUpperCase(),PASSWORD,false);
    assertEquals("STUDENT",login.call("GET","/auth/me",null,200).path("activeRole").asText());
    login.call("GET","/teacher/students",null,403);
    login.call("GET","/admin/users",null,403);
  }

  @Test void resetsPasswordAndRevokesJwtSessionAndEventSubscription() throws Exception {
    String email = mailbox();
    Client jwt = student(email);
    Client session = new Client();
    session.login(email,PASSWORD,true);
    var stream = jwt.perform("GET","/events",null);
    assertTrue(stream.getRequest().isAsyncStarted());
    jdbc.update("DELETE FROM email_send_quota");
    codes.remove(email);
    var publicClient = new Client(); publicClient.csrf();
    publicClient.call("POST","/auth/email/code",obj("purpose","RESET","email",mailbox()),202);
    publicClient.call("POST","/auth/email/code",obj("purpose","RESET","email",email),202);
    String challenge = awaitCode(email);
    String wrong = "000000".equals(challenge) ? "999999" : "000000";
    var body = obj("email",email,"code",wrong,"password",REPLACEMENT,"confirmPassword",REPLACEMENT);
    publicClient.call("POST","/auth/password/reset",body,422);
    body.put("code",challenge);
    publicClient.call("POST","/auth/password/reset",body,204);
    publicClient.call("POST","/auth/password/reset",body,422);
    String event = publishFor(users.byEmail(email).id());
    eventService.deliver();
    stream.getAsyncResult(3000);
    assertFalse(stream.getResponse().getContentAsString().contains(event));
    jwt.call("GET","/events",null,401);
    jwt.call("GET","/auth/me",null,401);
    session.call("GET","/auth/me",null,401);
    publicClient.call("POST","/auth/login",obj("email",email,"password",PASSWORD),401);
    publicClient.login(email,REPLACEMENT,false);
  }

  @Test void rejectsExpiredAndExhaustedResetChallenges() throws Exception {
    String email = mailbox();
    student(email);
    Client client = new Client(); client.csrf();
    jdbc.update("DELETE FROM email_send_quota");
    codes.remove(email);
    client.call("POST","/auth/email/code",obj("purpose","RESET","email",email),202);
    String first = awaitCode(email);
    jdbc.update("UPDATE email_challenges SET expires_at=? WHERE purpose='RESET' AND email=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),email);
    var body = obj("email",email,"code",first,"password",REPLACEMENT,"confirmPassword",REPLACEMENT);
    client.call("POST","/auth/password/reset",body,422);
    jdbc.update("DELETE FROM email_send_quota");
    codes.remove(email);
    client.call("POST","/auth/email/code",obj("purpose","RESET","email",email),202);
    String correct = awaitCode(email);
    body.put("code", "000000".equals(correct) ? "999999" : "000000");
    for (int i=0; i<5; i++) client.call("POST","/auth/password/reset",body,422);
    body.put("code",correct);
    client.call("POST","/auth/password/reset",body,422);
    client.login(email,PASSWORD,false);
  }

  @Test void changesVerifiedEmailAndRevokesOldCredentials() throws Exception {
    String before = mailbox(), after = mailbox();
    Client current = student(before);
    String user = users.byEmail(before).id();
    current.call("POST","/auth/email/change-code",obj("email",after,"currentPassword","WrongPass2026!"),401);
    current.call("POST","/auth/email/change-code",obj("email",after,"currentPassword",PASSWORD),202);
    var change = obj("email",after,"currentPassword",PASSWORD,"code",awaitCode(after));
    current.call("POST","/auth/email/change",change,204);
    current.call("GET","/auth/me",null,401);
    assertNull(users.byEmail(before));
    assertEquals(user,users.byEmail(after).id());
    verify(mail).send(eq(before),eq("研途 AI 邮箱变更通知"),anyString());
    var login = new Client(); login.csrf();
    login.call("POST","/auth/login",obj("email",before,"password",PASSWORD),401);
    login.login(after,PASSWORD,false);
  }

  private String awaitCode(String email) throws InterruptedException {
    for (int n=0; n<100 && !codes.containsKey(email); n++) Thread.sleep(20);
    assertNotNull(codes.get(email));
    return codes.get(email);
  }

  @Test void legacyBindingPreservesIdAndPrivateRecordsWithoutMergingEmails() throws Exception {
    Client operator = new Client(); operator.login(ADMIN,PASSWORD,false);
    String id = id(), other = id(), resource = id(), email = mailbox();
    String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode(PASSWORD);
    for (String user : new String[]{id,other}) {
      jdbc.update("INSERT INTO app_users(id,login_name,display_name,password_hash) VALUES(?,?,?,?)",
          user,"legacy-" + user,"历史学生",hash);
      jdbc.update("INSERT INTO sys_user_roles(user_id,role_code) VALUES(?,'STUDENT')",user);
    }
    jdbc.update("INSERT INTO conversations(id,owner_id,version,payload) VALUES(?,?,1,'{}')",resource,id);
    assertTrue(operator.call("GET","/admin/users/legacy",null,200).path("items").toString().contains(id));
    JsonNode request = obj("email",email,"reason","核对历史账号");
    operator.call("POST","/admin/users/" + id + "/legacy-binding",request,202);
    operator.call("POST","/admin/users/" + other + "/legacy-binding",request,409);
    String token = links.get(email);
    var client = new Client(); client.csrf();
    assertTrue(client.call("POST","/auth/invitation/check",obj("token",token),200).path("legacy").asBoolean());
    jdbc.update("DELETE FROM email_send_quota");
    client.call("POST","/auth/email/code",obj("purpose","ACTIVATE","token",token),202);
    var body = obj("token",token,"code",awaitCode(email),"password",REPLACEMENT,
        "confirmPassword",REPLACEMENT,"legacyPassword","Incorrect2026!");
    client.call("POST","/auth/invitation/activate",body,422);
    body.put("legacyPassword",PASSWORD);
    client.call("POST","/auth/invitation/activate",body,204);
    client.login(email,REPLACEMENT,false);
    assertEquals(id,client.call("GET","/auth/me",null,200).path("id").asText());
    assertEquals(id,jdbc.queryForObject("SELECT owner_id FROM conversations WHERE id=?",String.class,resource));
    assertNull(users.byId(other).email());
  }

  @Test void rejectsExpiredAndRevokedInvitationsAndReportsMailFailure() throws Exception {
    Client operator = new Client(); operator.login(ADMIN,PASSWORD,false);
    Client publicUser = new Client(); publicUser.csrf();
    String email = mailbox();
    var input = obj("email",email,"displayName","指导老师","reason","测试撤销");
    operator.call("POST","/admin/teachers",input,201);
    String first = links.get(email);
    jdbc.update("UPDATE account_invitations SET expires_at=? WHERE email=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),email);
    publicUser.call("POST","/auth/invitation/check",obj("token",first),422);
    jdbc.update("DELETE FROM email_send_quota");
    String userPath = "/admin/users/" + users.byEmail(email).id() + "/invitation/";
    operator.call("POST",userPath + "resend",obj("reason","重发测试"),202);
    String second = links.get(email);
    assertNotEquals(first,second);
    operator.call("POST",userPath + "cancel",obj("reason","撤销测试"),200);
    publicUser.call("POST","/auth/invitation/check",obj("token",second),422);

    String failed = mailbox();
    doThrow(new com.kaoyan.ai.common.ApiException(503,"MAIL_UNAVAILABLE","测试发送失败"))
        .when(mail).send(eq(failed),anyString(),anyString());
    input.put("email",failed);
    operator.call("POST","/admin/teachers",input,503);
    assertEquals("FAILED",users.invitationByUser(users.byEmail(failed).id()).get("status"));
  }

  @Test void revokesPermissionFromExistingTokenAndStopsEventDelivery() throws Exception {
    Client operator = new Client(); operator.login(ADMIN,PASSWORD,false);
    Client student = student(mailbox());
    var stream = student.perform("GET","/events",null);
    assertTrue(stream.getRequest().isAsyncStarted());
    var roles = operator.call("GET","/admin/roles",null,200).path("items");
    var original = java.util.stream.StreamSupport.stream(roles.spliterator(),false)
        .filter(role -> role.path("code").asText().equals("STUDENT")).findFirst().orElseThrow().path("permissions");
    var revoked = arr();
    original.forEach(permission -> {
      if (!"student:study:use".equals(permission.asText())) revoked.add(permission);
    });
    try {
      operator.call("PUT","/admin/roles/STUDENT/permissions",obj("permissions",revoked,"reason","验证即时撤销"),200);
      student.call("GET","/events",null,403);
      String event = publishFor(student.call("GET","/auth/me",null,200).path("id").asText());
      eventService.deliver();
      stream.getAsyncResult(3000);
      assertFalse(stream.getResponse().getContentAsString().contains(event));
    } finally {
      operator.call("PUT","/admin/roles/STUDENT/permissions",obj("permissions",original,"reason","恢复测试数据"),200);
    }
  }

  @Autowired com.kaoyan.ai.service.task.EventService eventService;
  private String publishFor(String user) {
    String task = id();
    eventService.publish(user,"teaching.task.assigned",task,1,obj("taskId",task));
    return task;
  }

  @Test void issuesTeacherInvitationAndRejectsReuseAndUnprivilegedCalls() throws Exception {
    Client operator = new Client(); operator.login(ADMIN,PASSWORD,false);
    String email = mailbox();
    var teacher = obj("email",email,"displayName","指导老师","reason","测试教师开通");
    operator.call("POST","/admin/teachers",teacher,201);
    operator.call("GET","/admin/users",null,200);
    operator.call("POST","/admin/teachers",teacher,409);
    String token = links.get(email);
    assertNotNull(token);
    var client = new Client(); client.csrf();
    client.call("POST","/auth/login",obj("email",email,"password",PASSWORD),401);
    client.call("POST","/auth/invitation/check",obj("token",token),200);
    // Account creation and activation share the production mailbox cooldown.
    jdbc.update("DELETE FROM email_send_quota");
    client.call("POST","/auth/email/code",obj("purpose","ACTIVATE","token",token),202);
    var activation = obj("token",token,"code",codes.get(email),"password",PASSWORD,"confirmPassword",PASSWORD);
    client.call("POST","/auth/invitation/activate",activation,204);
    client.call("POST","/auth/invitation/activate",activation,422);
    client.login(email,PASSWORD,false);
    client.call("GET","/teacher/students",null,200);
    client.call("GET","/admin/users",null,403);
    client.call("POST","/admin/teachers",teacher,403);
    operator.call("GET","/qa/conversations/" + id() + "/messages",null,403);
    operator.call("GET","/admin/audit",null,200);
  }

  @Test void preventsLastAdministratorLossAndRevokesDisabledAccounts() throws Exception {
    Client operator = new Client(); operator.login(ADMIN,PASSWORD,false);
    String adminId = users.byEmail(ADMIN).id();
    operator.call("POST","/admin/users/" + adminId + "/disable",obj("reason","安全测试"),409);
    operator.call("PUT","/admin/roles/ADMIN/permissions",obj("permissions",arr("role:assign"),"reason","安全测试"),409);
    operator.call("PUT","/admin/roles/ADMIN/permissions",obj("permissions",
        arr("role:assign","permission:assign","user:enable","student:study:use"),"reason","安全测试"),403);
    String email = mailbox();
    Client student = student(email);
    String path = "/admin/users/" + users.byEmail(email).id();
    operator.call("PUT",path + "/roles",obj("roles",arr("STUDENT","TEACHER"),"reason","安全测试"),200);
    student.call("GET","/auth/me",null,401);
    student.login(email,PASSWORD,false);
    student.call("POST","/auth/workspace",obj("workspace","STUDENT"),200);
    student.csrf();
    operator.call("POST",path + "/disable",obj("reason","安全测试"),200);
    student.call("GET","/auth/me",null,401);
    var login = new Client(); login.csrf();
    login.call("POST","/auth/login",obj("email",email,"password",PASSWORD),401);
    operator.call("POST",path + "/enable",obj("reason","安全测试"),200);
    student.call("GET","/auth/me",null,401);
  }

  private static final class ClosingExecutor implements AutoCloseable {
    final java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2);
    @Override public void close() { executor.shutdownNow(); }
  }
}
