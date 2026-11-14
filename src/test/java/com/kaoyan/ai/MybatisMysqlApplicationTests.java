package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kaoyan.ai.service.task.*;
import com.kaoyan.ai.persistence.mapper.IdentityMapper;
import com.kaoyan.ai.service.auth.AccountMail;
import com.kaoyan.ai.service.auth.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 启动完整 MySQL 应用，验证配置、Flyway、控制器、认证和 MyBatis 的实际链路。 */
@EnabledIfSystemProperty(named = "mysql.integration", matches = "true")
@ActiveProfiles("mysql")
@SpringBootTest(classes = KaoyanAiApplication.class, properties = "app.seed-local=false")
@AutoConfigureMockMvc
class MybatisMysqlApplicationTests {
  // 验证期间不消费、恢复、交卷或提醒原有用户的任务。
  @MockBean JobRecovery recovery;
  @MockBean JobWorker worker;
  @MockBean ExamDeadlineService deadlines;
  @MockBean ReminderService reminders;
  @MockBean PolicyImportService policies;
  @Autowired MockMvc mvc;
  @MockBean AccountMail mail;
  @Autowired IdentityMapper accounts;
  @Autowired EmailVerificationService codes;

  @Test
  @Transactional
  void fullMysqlApplicationRegistersAuthenticatesAndReadsProfile() throws Exception {
    mvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
    var csrfResponse = mvc.perform(get("/api/v1/session/csrf"))
        .andExpect(status().isOk()).andReturn();
    var session = (MockHttpSession) csrfResponse.getRequest().getSession();
    String csrf = parse(csrfResponse.getResponse().getContentAsString())
        .path("data").path("token").asText();
    String email = "mb_" + id().replace("-", "").substring(0, 20) + "@example.test";
    codes.send(EmailVerificationService.Purpose.REGISTER, email, "");
    var content = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(mail).send(org.mockito.ArgumentMatchers.eq(email),
        org.mockito.ArgumentMatchers.anyString(),content.capture());
    String code = java.util.regex.Pattern.compile("[0-9]{6}").matcher(content.getValue()).results()
        .findFirst().orElseThrow().group();
    var registered = mvc.perform(post("/api/v1/auth/register").session(session)
        .header("X-CSRF-Token", csrf).contentType(MediaType.APPLICATION_JSON)
        .content(obj("email", email, "code", code, "acceptedTerms", true,
            "password", "MybatisVerify2026!", "confirmPassword", "MybatisVerify2026!",
            "displayName", "MySQL链路验证").toString()))
        .andExpect(status().isCreated()).andReturn();
    var account = accounts.byEmail(email);
    assertTrue(account.passwordHash().startsWith("$2"));
    var cookie = registered.getResponse().getCookie("AUTH_TOKEN");
    assertNotNull(cookie);
    mvc.perform(get("/api/v1/auth/me").cookie(cookie))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(account.id()));
    mvc.perform(get("/api/v1/profile").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.displayName").value("MySQL链路验证"));
  }
}
