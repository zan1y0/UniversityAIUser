package com.kaoyan.ai.service.auth;

import static com.kaoyan.ai.common.Jsons.*;
import static com.kaoyan.ai.service.auth.AccountInputs.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.entity.EmailAccount;
import com.kaoyan.ai.persistence.mapper.IdentityMapper;
import com.kaoyan.ai.persistence.mapper.RbacMapper;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import jakarta.servlet.http.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailAuthService {
  private final IdentityMapper accounts;
  private final RbacMapper roles;
  private final PermissionService permissions;
  private final JwtService jwt;
  private final SessionService sessions;
  private final AuthenticationManager manager;
  private final EmailVerificationService verification;
  private final RecordRepository records;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private final Map<String, Window> attempts = new HashMap<>();
  private record Window(long until, int count) {}

  EmailAuthService(IdentityMapper accounts, RbacMapper roles, PermissionService permissions, JwtService jwt,
      SessionService sessions, AuthenticationManager manager, EmailVerificationService verification, RecordRepository records) {
    this.accounts = accounts;
    this.roles = roles;
    this.permissions = permissions;
    this.jwt = jwt;
    this.sessions = sessions;
    this.manager = manager;
    this.verification = verification;
    this.records = records;
  }

  @Transactional
  public ObjectNode login(JsonNode body, HttpServletRequest request, HttpServletResponse response, boolean legacySession) {
    throttle(request.getRemoteAddr());
    fields(body, "email", "password");
    String mailbox = email(string(body, "email"));
    EmailAccount account = authenticate(mailbox, credential(body, "password"));
    var granted = roles.roles(account.id());
    String role = List.of("ADMIN","TEACHER","STUDENT").stream().filter(granted::contains).findFirst()
        .orElseThrow(EmailAuthService::invalid);
    return establish(account, role, request, response, legacySession);
  }

  @Transactional(noRollbackFor = EmailVerificationService.InvalidCode.class)
  public ObjectNode register(JsonNode body, HttpServletRequest request, HttpServletResponse response) {
    fields(body, "email", "code", "password", "confirmPassword", "displayName", "acceptedTerms");
    require(body.path("acceptedTerms").isBoolean() && body.path("acceptedTerms").booleanValue(), "请阅读并同意用户协议");
    String mailbox = email(string(body, "email"));
    String name = name(string(body, "displayName"));
    String password = password(body);
    if (accounts.byEmail(mailbox) != null)
      throw new ApiException(409, "ACCOUNT_EXISTS", "该邮箱已被占用");
    verification.consume(EmailVerificationService.Purpose.REGISTER, mailbox, "", string(body, "code"));
    String user = id();
    insert(user, mailbox, name, encoder.encode(password), "ENABLED", true, true);
    roles.grant(user, "STUDENT");
    createProfile(user, mailbox, name);
    return establish(accounts.byId(user), "STUDENT", request, response, false);
  }

  public void insert(String id, String email, String name, String hash, String status, boolean verified, boolean terms) {
    try {
      accounts.insertAccount(id, email, name, hash, status, verified, terms);
    } catch (DuplicateKeyException e) {
      throw new ApiException(409, "ACCOUNT_EXISTS", "该邮箱已被占用");
    }
  }

  public void createProfile(String id, String email, String name) {
    records.create("profiles", id, id, obj("user", obj("id", id, "loginName", email, "displayName", name),
        "undergraduateMajor", "尚未填写", "targetProgramId", null, "targetDate", null, "dailyMinutes", 120,
        "timezone", "Asia/Shanghai", "baseline", arr(), "activePlanId", null, "version", 1));
  }

  @Transactional
  public ObjectNode switchRole(JsonNode body, HttpServletRequest request, HttpServletResponse response) {
    fields(body, "workspace");
    String user = sessions.user(request);
    String workspace = string(body, "workspace");
    EmailAccount account = accounts.lockUser(user);
    if (account == null || !account.enabled()) throw invalid();
    permissions.requireRole(user, workspace);
    return establish(account, workspace, request, response, false);
  }

  public ObjectNode current(HttpServletRequest request) {
    return view(accounts.byId(sessions.user(request)), sessions.role(request));
  }

  public EmailAccount authenticate(String mailbox, String password) {
    try {
      String user = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(mailbox, password)).getName();
      var account = accounts.lockUser(user);
      if (account == null || !mailbox.equals(account.email())) throw invalid();
      verifyPassword(account, password);
      return account;
    } catch (AuthenticationException e) {
      throw invalid();
    }
  }

  public void verifyPassword(EmailAccount account, String password) {
    if (account == null || !account.enabled() || !encoder.matches(password, account.passwordHash())) throw invalid();
  }

  public String hash(String password) { return encoder.encode(password); }

  boolean matchesStored(EmailAccount account, String password) {
    return account != null && account.passwordHash().startsWith("$2") && encoder.matches(password, account.passwordHash());
  }

  public boolean matchesLegacy(EmailAccount account, String password) {
    String teacherHash = accounts.legacyTeacherHash(account.id());
    return (account.passwordHash().startsWith("$2") && encoder.matches(password, account.passwordHash()))
        || (teacherHash != null && encoder.matches(password, teacherHash));
  }

  private ObjectNode establish(EmailAccount account, String role, HttpServletRequest request,
      HttpServletResponse response, boolean legacySession) {
    try {
      if (legacySession) jwt.revoke(request, response);
      else jwt.revokeActive(request);
    } catch (ApiException e) {
      if (e.status() != 401) throw e;
      if (legacySession) jwt.clear(response);
    }
    var session = request.getSession(true);
    request.changeSessionId();
    session.removeAttribute("csrf");
    session.removeAttribute("userId");
    session.removeAttribute("activeRole");
    session.removeAttribute("authVersion");
    if (legacySession) {
      session.setAttribute("userId", account.id());
      session.setAttribute("activeRole", role);
      session.setAttribute("authVersion", account.authVersion());
    } else {
      jwt.issue(account.id(), role, account.authVersion(), response);
    }
    return view(account, role);
  }

  private ObjectNode view(EmailAccount account, String role) {
    if (account == null || !account.enabled()) throw invalid();
    return obj("id", account.id(), "email", account.email(), "displayName", account.displayName(),
        "activeRole", role, "roles", roles.roles(account.id()), "permissions", permissions.permissions(role));
  }

  private synchronized void throttle(String source) {
    long now = System.currentTimeMillis();
    attempts.entrySet().removeIf(entry -> entry.getValue().until() <= now);
    Window window = attempts.getOrDefault(source, new Window(now + 60000, 0));
    if (window.count() >= 10 || (!attempts.containsKey(source) && attempts.size() >= 10000))
      throw new ApiException(429, "RATE_LIMITED", "登录尝试过多，请稍后重试");
    attempts.put(source, new Window(window.until(), window.count() + 1));
  }

  private static ApiException invalid() {
    return new ApiException(401, "INVALID_CREDENTIALS", "邮箱或密码错误，或账号尚不可用");
  }
}
