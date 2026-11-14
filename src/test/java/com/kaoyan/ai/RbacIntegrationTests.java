package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.api.contract.ContractRegistry;
import com.kaoyan.ai.persistence.entity.UserAccount;
import com.kaoyan.ai.persistence.mapper.RbacMapper;
import com.kaoyan.ai.persistence.repository.UserAccountRepository;
import com.kaoyan.ai.service.task.*;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes=KaoyanAiApplication.class,properties={
  "spring.datasource.url=jdbc:h2:mem:rbac;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
  "app.seed-local=false"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Transactional
class RbacIntegrationTests {
  @MockBean JobRecovery recovery;
  @MockBean JobWorker worker;
  @MockBean ExamDeadlineService deadline;
  @MockBean ReminderService reminder;
  @MockBean PolicyImportService policy;
  @Autowired MockMvc mvc;
  @Autowired UserAccountRepository users;
  @Autowired RbacMapper roles;
  @Autowired com.kaoyan.ai.persistence.mapper.IdentityMapper identities;
  @Autowired ContractRegistry contract;
  private static final String PASSWORD="RbacTest2026!";
  private String student,other,teacher,anotherTeacher;
  private String studentName,otherName,teacherName,anotherName;

  private static class Client {
    private static final java.util.concurrent.atomic.AtomicInteger sequence=new java.util.concurrent.atomic.AtomicInteger();
    final String ip="198.18.0."+sequence.incrementAndGet();
    MockHttpSession session; Cookie cookie; String csrf;
  }
  @BeforeEach void accounts() {
    studentName="s_"+id().substring(0,8);otherName="o_"+id().substring(0,8);
    teacherName="t_"+id().substring(0,8);anotherName="u_"+id().substring(0,8);
    student=account(studentName,false);other=account(otherName,false);
    teacher=account(teacherName,true);anotherTeacher=account(anotherName,true);
  }
  private String account(String name,boolean teacher) {
    String user=id();
    var account = new UserAccount(user, name, name, new BCryptPasswordEncoder(4).encode(PASSWORD));
    if (teacher) users.createTeacher(account);
    else users.create(account);
    identities.markDevelopmentAccount(user, name + "@example.test");
    if (teacher) identities.changePassword(user, account.passwordHash());
    return user;
  }
  private MvcResult request(Client c,String method,String path,JsonNode body,int expected) throws Exception {
    var req=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(method),"/api/v1"+path);
    req.with(r->{r.setRemoteAddr(c.ip);return r;});
    if(c.session!=null)req.session(c.session);
    if(c.cookie!=null)req.cookie(c.cookie);
    if(c.csrf!=null)req.header("X-CSRF-Token",c.csrf);
    if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(body.toString());
    var result=mvc.perform(req).andReturn();
    assertEquals(expected,result.getResponse().getStatus(),
        method+" "+path+" "+result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    c.session=(MockHttpSession)result.getRequest().getSession(false);
    if(result.getResponse().getCookie("AUTH_TOKEN")!=null)c.cookie=result.getResponse().getCookie("AUTH_TOKEN");
    return result;
  }
  private JsonNode data(Client c,String method,String path,JsonNode body,int expected) throws Exception {
    return parse(request(c,method,path,body,expected).getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
  }
  private void csrf(Client c) throws Exception {
    c.csrf=data(c,"GET","/session/csrf",null,200).path("token").asText();
  }
  private Client login(String name,String role) throws Exception {
    Client c=new Client();csrf(c);
    var identity=data(c,"POST","/auth/login",obj("email",name+"@example.test","password",PASSWORD),200);
    assertEquals(role,identity.path("activeRole").asText());csrf(c);return c;
  }
  private JsonNode task(Client t) throws Exception {
    return data(t,"POST","/teacher/tasks",obj("studentId",student,"title","练习并复盘",
        "description","完成数学专项练习","deadline",Instant.now().plusSeconds(3600).toString()),201);
  }

  @Test void rolesAreNotGrantedByLoginOrRegistrationAndRevocationIsImmediate() throws Exception {
    Client c=new Client();csrf(c);
    request(c,"POST","/auth/login",obj("email",studentName+"@example.test","password",PASSWORD,"role","TEACHER"),422);
    request(c,"POST","/auth/login",obj("email",teacherName+"@example.test","password",PASSWORD,"role","STUDENT"),422);
    request(c,"POST","/auth/register",obj("loginName","forged_"+id().substring(0,8),"password",PASSWORD,"displayName","伪造","role","TEACHER"),422);
    var s=login(studentName,"STUDENT");request(s,"GET","/teacher/students",null,403);
    var t=login(teacherName,"TEACHER");request(t,"GET","/teacher/students",null,200);
    request(t,"GET","/analytics/overview?from=2026-11-01&to=2026-11-08&timezone=Asia/Shanghai",null,403);
    roles.revoke(teacher,"TEACHER");request(t,"GET","/teacher/students",null,403);
  }

  @Test void guidanceTaskAndStudentCompletionRoundTrip() throws Exception {
    var s=login(studentName,"STUDENT");var t=login(teacherName,"TEACHER");
    assertEquals(0,data(t,"GET","/teacher/students",null,200).path("items").size());
    data(s,"POST","/teaching/teachers/"+teacher,null,200);
    assertEquals(student,data(t,"GET","/teacher/students",null,200).path("items").get(0).path("id").asText());
    var summary=data(t,"GET","/teacher/students/"+student+"/summary",null,200);
    assertEquals(0,summary.path("statistics").path("attemptCount").asInt());
    assertFalse(summary.toString().contains("password"));
    var work=task(t);String id=work.path("id").asText();
    assertEquals(1,data(s,"GET","/teaching/tasks",null,200).path("items").size());
    var finished=data(s,"PATCH","/teaching/tasks/"+id+"/complete",obj("version",1),200);
    assertEquals("completed",finished.path("status").asText());assertEquals(2,finished.path("version").asInt());
    request(s,"PATCH","/teaching/tasks/"+id+"/complete",obj("version",1),409);
    assertEquals("completed",data(t,"GET","/teacher/tasks",null,200).path("items").get(0).path("status").asText());
    var cancelled=task(t);
    assertEquals("cancelled",data(t,"PATCH","/teacher/tasks/"+cancelled.path("id").asText()+"/cancel",obj("version",1),200).path("status").asText());
  }

  @Test void ownershipAndConsentPreventCrossUserAccessAndWrites() throws Exception {
    var s=login(studentName,"STUDENT");var o=login(otherName,"STUDENT");
    var t=login(teacherName,"TEACHER");var u=login(anotherName,"TEACHER");
    request(t,"GET","/teacher/students/"+student+"/summary",null,403);
    request(s,"POST","/teaching/teachers/"+teacher,null,200);var work=task(t);String id=work.path("id").asText();
    request(u,"GET","/teacher/students/"+student+"/summary",null,403);
    request(u,"POST","/teacher/tasks",obj("studentId",student,"title","越权","description","","deadline",Instant.now().plusSeconds(300).toString()),403);
    request(u,"PATCH","/teacher/tasks/"+id+"/cancel",obj("version",1),404);
    request(o,"PATCH","/teaching/tasks/"+id+"/complete",obj("version",1),404);
    request(s,"PATCH","/teaching/tasks/"+id+"/complete",obj("version",1,"studentId",other),400);
    request(s,"DELETE","/teaching/teachers/"+teacher,null,200);
    request(t,"GET","/teacher/students/"+student+"/summary",null,403);
    request(t,"PATCH","/teacher/tasks/"+id+"/cancel",obj("version",1),403);
    request(s,"PATCH","/teaching/tasks/"+id+"/complete",obj("version",1),403);
    assertEquals(0,data(s,"GET","/teaching/tasks",null,200).path("items").size());
  }

  @Test void teachingWritesRequireCsrfAndMissingIdentityIs401() throws Exception {
    request(new Client(),"GET","/teacher/students",null,401);
    var s=login(studentName,"STUDENT");s.csrf=null;
    request(s,"POST","/teaching/teachers/"+teacher,null,403);
  }

  @Test void classCodeEnrollmentAssignmentSubmissionAndGrading() throws Exception {
    var s = login(studentName, "STUDENT");
    var o = login(otherName, "STUDENT");
    var t = login(teacherName, "TEACHER");
    var u = login(anotherName, "TEACHER");
    String code = id().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    String classId = data(t, "POST", "/teacher/classes", obj("name", "数学一班", "code", code), 201).path("id").asText();
    request(u, "POST", "/teacher/classes", obj("name", "重复班级", "code", code.toLowerCase(java.util.Locale.ROOT)), 409);
    request(s, "POST", "/teacher/classes", obj("name", "越权", "code", code), 403);
    for (var member : java.util.List.of(s, o)) {
      assertEquals(classId, data(member, "POST", "/teaching/classes/join",
          obj("code", code.toLowerCase(java.util.Locale.ROOT)), 200).path("id").asText());
    }
    request(s, "POST", "/teaching/classes/join", obj("code", code), 200);
    assertEquals(2, data(t, "GET", "/teacher/classes", null, 200).path("items").get(0).path("memberCount").asInt());
    assertEquals(1, data(s, "GET", "/teaching/classes", null, 200).path("items").size());
    assertEquals(0, data(u, "GET", "/teacher/classes", null, 200).path("items").size());

    var work = obj("title", "解题过程", "description", "提交详细步骤",
        "deadline", Instant.now().plusSeconds(3600).toString(), "requiresSubmission", true);
    request(u, "POST", "/teacher/classes/" + classId + "/tasks", work, 404);
    assertEquals(2, data(t, "POST", "/teacher/classes/" + classId + "/tasks", work, 201).path("assignedCount").asInt());
    var assigned = data(s, "GET", "/teaching/tasks", null, 200).path("items").get(0);
    assertEquals(classId, assigned.path("classId").asText());
    assertTrue(assigned.path("requiresSubmission").asBoolean());
    String taskId = assigned.path("id").asText();
    var notices = data(s, "GET", "/news/notifications?unreadOnly=true", null, 200);
    assertEquals(1, notices.path("total").asInt());
    var notice = notices.path("items").get(0);
    assertEquals("teaching.task.assigned", notice.path("type").asText());
    assertEquals(taskId, notice.path("resourceId").asText());
    assertTrue(notice.path("content").asText().contains("数学一班（" + code + "）"));
    assertTrue(notice.path("content").asText().contains("截止时间"));
    assertTrue(notice.path("createdAt").asText().endsWith("Z"));
    var otherNotice = data(o, "GET", "/news/notifications?unreadOnly=true", null, 200).path("items").get(0);
    assertNotEquals(taskId, otherNotice.path("resourceId").asText());
    String readNotice = "/news/notifications/" + notice.path("id").asText() + "/read";
    request(o, "POST", readNotice, obj("version", 1), 404);
    assertTrue(data(s, "POST", readNotice, obj("version", 1), 200).path("read").asBoolean());
    assertEquals(0, data(s, "GET", "/news/notifications?unreadOnly=true", null, 200).path("total").asInt());
    String submit = "/teaching/tasks/" + taskId + "/submission";
    String grade = "/teacher/tasks/" + taskId + "/grade";
    request(s, "PATCH", "/teaching/tasks/" + taskId + "/complete", obj("version", 1), 409);
    request(t, "PATCH", grade, obj("version", 1, "score", 80, "feedback", ""), 409);
    request(o, "POST", submit, obj("version", 1, "content", "越权提交"), 404);
    var submitted = data(s, "POST", submit, obj("version", 1, "content", "先写出方程，再求解。"), 201);
    assertEquals("submitted", submitted.path("status").asText());
    assertTrue(submitted.path("submission").path("score").isNull());
    request(s, "POST", submit, obj("version", 1, "content", "重复提交"), 409);
    request(u, "PATCH", grade, obj("version", 2, "score", 80, "feedback", ""), 404);
    request(t, "PATCH", grade, obj("version", 2, "score", 101, "feedback", ""), 400);
    var graded = data(t, "PATCH", grade, obj("version", 2, "score", 95, "feedback", "步骤清楚。"), 200);
    assertEquals("graded", graded.path("status").asText());
    request(t, "PATCH", grade, obj("version", 2, "score", 50, "feedback", ""), 409);
    var result = data(s, "GET", "/teaching/tasks", null, 200).path("items").get(0).path("submission");
    assertEquals(95, result.path("score").asInt());
    assertEquals("步骤清楚。", result.path("feedback").asText());
    request(s, "DELETE", "/teaching/classes/" + classId, null, 200);
    assertEquals(0, data(s, "GET", "/teaching/tasks", null, 200).path("items").size());
  }

  @org.springframework.test.context.transaction.AfterTransaction
  void mysqlAndH2TestAccountsAreRolledBack() {
    assertTrue(users.findById(student).isEmpty());
    assertTrue(users.findById(teacher).isEmpty());
  }

  @Test void publishesOneTaskAcrossClassesAndRestrictsAttachmentDownloads() throws Exception {
    var t = login(teacherName, "TEACHER");
    var s = login(studentName, "STUDENT");
    var o = login(otherName, "STUDENT");
    var unrelatedTeacher = login(anotherName, "TEACHER");
    String codeA = id().substring(0, 8);
    String codeB = id().substring(0, 8);
    String classA = data(t, "POST", "/teacher/classes", obj("name", "甲班", "code", codeA), 201).path("id").asText();
    String classB = data(t, "POST", "/teacher/classes", obj("name", "乙班", "code", codeB), 201).path("id").asText();
    request(s, "POST", "/teaching/classes/join", obj("code", codeA), 200);
    request(o, "POST", "/teaching/classes/join", obj("code", codeA), 200);
    request(o, "POST", "/teaching/classes/join", obj("code", codeB), 200);
    assertEquals(2, data(t, "GET", "/teacher/classes/" + classA + "/students", null, 200).path("items").size());
    request(unrelatedTeacher, "GET", "/teacher/classes/" + classA + "/students", null, 404);
    var batch = obj("title", "作业附件", "description", "请上传附件",
        "deadline", Instant.now().plusSeconds(3600).toString(), "requiresSubmission", true,
        "classes", arr(obj("classId", classA, "studentIds", arr(student)), obj("classId", classB)));
    request(unrelatedTeacher, "POST", "/teacher/tasks/publish", batch, 404);
    assertEquals(2, data(t, "POST", "/teacher/tasks/publish", batch, 201).path("assignedCount").asInt());
    var studentTasks = data(s, "GET", "/teaching/tasks", null, 200).path("items");
    var otherTasks = data(o, "GET", "/teaching/tasks", null, 200).path("items");
    assertEquals(1, studentTasks.size());
    assertEquals(1, otherTasks.size());
    assertEquals(classB, otherTasks.get(0).path("classId").asText());
    assertEquals(studentTasks.get(0).path("publicationId"), otherTasks.get(0).path("publicationId"));
    assertEquals(1, data(s, "GET", "/news/notifications?unreadOnly=true", null, 200).path("total").asInt());
    String taskId = studentTasks.get(0).path("id").asText();
    String attachment = "/tasks/" + taskId + "/submission/attachment";
    byte[] bytes = "%PDF-1.7\nHomework".getBytes(StandardCharsets.UTF_8);
    var file = new MockMultipartFile("file", "答案.pdf", "application/pdf", bytes);
    var uploaded = mvc.perform(multipart("/api/v1/teaching" + attachment).file(file).param("version", "1")
        .session(s.session).cookie(s.cookie).header("X-CSRF-Token", s.csrf)).andReturn();
    assertEquals(201, uploaded.getResponse().getStatus(), uploaded.getResponse().getContentAsString(StandardCharsets.UTF_8));
    var submission = parse(uploaded.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").path("submission");
    assertEquals("", submission.path("content").asText());
    assertEquals("答案.pdf", submission.path("attachment").path("fileName").asText());
    assertArrayEquals(bytes, request(t, "GET", "/teacher" + attachment, null, 200).getResponse().getContentAsByteArray());
    assertArrayEquals(bytes, request(s, "GET", "/teaching" + attachment, null, 200).getResponse().getContentAsByteArray());
    request(o, "GET", "/teaching" + attachment, null, 404);
    request(unrelatedTeacher, "GET", "/teacher" + attachment, null, 404);
    assertEquals(97, data(t, "PATCH", "/teacher/tasks/" + taskId + "/grade",
        obj("version", 2, "score", 97, "feedback", "已检查附件"), 200).path("submission").path("score").asInt());
  }
}
