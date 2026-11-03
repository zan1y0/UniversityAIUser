package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.persistence.UserAccountRepository;
import com.kaoyan.ai.security.JwtService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.seed-local=false"})
@AutoConfigureMockMvc
@org.springframework.test.context.TestExecutionListeners(
    listeners = {org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        org.springframework.test.context.web.ServletTestExecutionListener.class},
    mergeMode = org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class AuthIntegrationTests {
  @Autowired MockMvc mvc;
  @Autowired UserAccountRepository accounts;
  @Autowired JdbcTemplate jdbc;
  private static final String PASSWORD = "Study2026!";
  private final String name = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
  private final String ip = UUID.randomUUID().toString();
  private MockHttpSession session;
  private String csrf;
  private Cookie cookie;

  private void csrf() throws Exception {
    var request = get("/api/v1/session/csrf");
    if (session != null) request.session(session);
    MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
    session = (MockHttpSession) result.getRequest().getSession(false);
    csrf = parse(result.getResponse().getContentAsString()).path("data").path("token").asText();
  }
  private MvcResult write(String route, JsonNode body, int expected) throws Exception {
    var request = post("/api/v1/auth/" + route).session(session).header("X-CSRF-Token", csrf)
        .with(r -> { r.setRemoteAddr(ip); return r; });
    if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body.toString());
    if (cookie != null) request.cookie(cookie);
    return mvc.perform(request).andExpect(status().is(expected)).andReturn();
  }
  private void register() throws Exception {
    csrf();
    var result = write("register", obj("loginName", name, "password", PASSWORD, "displayName", "萤火同学"), 201);
    cookie = result.getResponse().getCookie(JwtService.COOKIE);
    assertNotNull(cookie);
    assertTrue(cookie.isHttpOnly());
    assertEquals("/api/v1", cookie.getPath());
    assertTrue(result.getResponse().getHeader("Set-Cookie").contains("SameSite=Lax"));
    assertEquals(3, cookie.getValue().split("\\.").length);
    assertFalse(result.getResponse().getContentAsString().contains("password"));
    csrf();
  }

  @Test void registerPersistRestoreAndLogoutRevokesJwt() throws Exception {
    register();
    var account = accounts.findByLoginName(name).orElseThrow();
    assertNotEquals(PASSWORD, account.passwordHash());
    assertTrue(new BCryptPasswordEncoder().matches(PASSWORD, account.passwordHash()));
    mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.displayName").value("萤火同学"));
    mvc.perform(get("/api/v1/profile").cookie(cookie)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.id").value(account.id()));
    Cookie replay = cookie;
    write("logout", null, 204);
    mvc.perform(get("/api/v1/auth/me").cookie(replay)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/profile").session(new MockHttpSession())).andExpect(status().isUnauthorized());
  }

  @Test void loginAfterRegistrationAndDuplicateName() throws Exception {
    register();
    write("register", obj("loginName", name.toUpperCase(), "password", PASSWORD, "displayName", "重复"), 409);
    write("login", obj("loginName", name, "password", "wrongPass123"), 401);
    var result = write("login", obj("loginName", name.toUpperCase(), "password", PASSWORD), 200);
    Cookie latest = result.getResponse().getCookie(JwtService.COOKIE);
    mvc.perform(get("/api/v1/auth/me").cookie(latest)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @Test void rejectsMissingCsrfAndUntrustedOrigins() throws Exception {
    csrf();
    var body = obj("loginName", name, "password", PASSWORD, "displayName", "测试").toString();
    mvc.perform(post("/api/v1/auth/register").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/register").session(session).header("X-CSRF-Token", csrf)
        .header("Origin", "https://attacker.example").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
  }

  @Test void rejectsInjectionAndPersistsNicknameAsData() throws Exception {
    csrf();
    String injection = "x' OR 1=1 --";
    write("register", obj("loginName", injection, "password", PASSWORD, "displayName", "同学"), 422);
    write("login", obj("loginName", injection, "password", PASSWORD), 422);
    assertTrue(accounts.findByLoginName(injection).isEmpty());
    String payload = "'); DROP TABLE app_users; --";
    write("register", obj("loginName", name, "password", PASSWORD, "displayName", payload), 201);
    assertEquals(payload, accounts.findByLoginName(name).orElseThrow().displayName());
    assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class) > 0);
  }

  @Test void rejectsForgedExpiredAndUnsignedTokens() throws Exception {
    register();
    String token = cookie.getValue();
    String[] parts = token.split("\\.");
    String altered = parts[0] + "." + parts[1] + "." + (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
    for (String invalid : new String[] {altered, "eyJhbGciOiJub25lIn0.e30.", "invalid"})
      mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + invalid)).andExpect(status().isUnauthorized());
    jdbc.update("UPDATE auth_tokens SET expires_at=? WHERE user_id=?", Timestamp.from(Instant.now().minusSeconds(1)), accounts.findByLoginName(name).orElseThrow().id());
    mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @Test void rejectsWeakPasswordsAndWrongTypes() throws Exception {
    csrf();
    for (String password : new String[] {"short", "onlyletters", "1234567890", "a1".repeat(37)})
      write("register", obj("loginName", name, "password", password, "displayName", "同学"), 422);
    write("register", obj("loginName", name, "password", 12345678, "displayName", "同学"), 422);
    assertTrue(accounts.findByLoginName(name).isEmpty());
  }

  @Test void limitsAttemptsAcrossNewSessions() throws Exception {
    for (int i = 0; i < 11; i++) {
      session = null;
      csrf();
      write("login", obj("loginName", name, "password", "wrongPass123"), i < 10 ? 401 : 429);
    }
  }

  @Test void protectedEndpointsRequireAuthentication() throws Exception {
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
  }

  @Test void rejectsSignedExpiredWrongIssuerAndAudience() throws Exception {
    register();
    var key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    JsonNode payload = parse(new String(java.util.Base64.getUrlDecoder().decode(cookie.getValue().split("\\.")[1]), StandardCharsets.UTF_8));
    for (String invalid : List.of("expired", "issuer", "audience")) {
      Instant now = Instant.now();
      var claims = JwtClaimsSet.builder().id(payload.path("jti").asText())
          .subject(payload.path("sub").asText()).issuedAt(now.minusSeconds(3600))
          .expiresAt(invalid.equals("expired") ? now.minusSeconds(120) : now.plusSeconds(1800))
          .issuer(invalid.equals("issuer") ? "attacker" : "university-ai-user")
          .audience(List.of(invalid.equals("audience") ? "other-app" : "university-ai-web")).build();
      String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
      mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
  }
}
