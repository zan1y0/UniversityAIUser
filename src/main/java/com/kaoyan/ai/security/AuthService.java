package com.kaoyan.ai.security;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.UserAccountRepository;
import com.kaoyan.ai.persistence.RecordRepository;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserAccountRepository accounts;
  private final JwtService jwt;
  private final SessionService sessions;
  private final RecordRepository records;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private final String dummyHash = encoder.encode(UUID.randomUUID().toString());
  private final Map<String, Window> windows = new HashMap<>();
  private record Window(long end, int count) {}

  public AuthService(UserAccountRepository accounts, JwtService jwt, SessionService sessions, RecordRepository records) {
    this.accounts = accounts; this.jwt = jwt; this.sessions = sessions;
    this.records = records;
  }

  /** 按连接来源限流，换 Cookie 不会重置；不信任客户端伪造的 X-Forwarded-For。 */
  private synchronized void throttle(HttpServletRequest request) {
    long now = System.currentTimeMillis();
    windows.entrySet().removeIf(entry -> entry.getValue().end() <= now);
    String key = request.getRemoteAddr();
    Window old = windows.getOrDefault(key, new Window(now + 60000, 0));
    if (old.count() >= 10 || (!windows.containsKey(key) && windows.size() >= 10000))
      throw new ApiException(429, "RATE_LIMITED", "尝试次数过多，请一分钟后重试");
    windows.put(key, new Window(old.end(), old.count() + 1));
  }

  @Transactional
  public ObjectNode authenticate(JsonNode body, boolean register, HttpServletRequest request, HttpServletResponse response) {
    throttle(request);
    if (body == null || !body.isObject()) throw new ApiException(422, "VALIDATION_FAILED", "请输入账号和密码");
    String name = field(body, "loginName").trim().toLowerCase(Locale.ROOT);
    String password = field(body, "password");
    require(name.matches("[a-z0-9_]{3,32}"), "账号需为3～32位字母、数字或下划线");
    require(password.length() >= 8 && password.getBytes(StandardCharsets.UTF_8).length <= 72,
        "密码至少8个字符，UTF-8编码长度不超过72字节");
    UserAccountRepository.Account account;
    if (register) {
      require(password.matches("(?s).*[A-Za-z].*") && password.matches("(?s).*\\d.*"), "密码必须同时包含字母和数字");
      String displayName = field(body, "displayName").trim();
      require(!displayName.isBlank() && displayName.length() <= 32
          && displayName.chars().noneMatch(Character::isISOControl), "昵称需为1～32个字符");
      account = new UserAccountRepository.Account(id(), name, displayName, encoder.encode(password));
      try { accounts.create(account); }
      catch (DuplicateKeyException e) { throw new ApiException(409, "ACCOUNT_EXISTS", "该账号已注册，请直接登录"); }
      records.create("profiles", account.id(), account.id(), obj(
          "user", view(account), "undergraduateMajor", "尚未填写", "targetProgramId", null,
          "targetDate", null, "dailyMinutes", 120, "timezone", "Asia/Shanghai",
          "baseline", arr(), "activePlanId", null, "version", 1));
    } else {
      var found = accounts.findByLoginName(name);
      boolean matches = encoder.matches(password, found.map(UserAccountRepository.Account::passwordHash).orElse(dummyHash));
      if (found.isEmpty() || !matches) throw new ApiException(401, "INVALID_CREDENTIALS", "账号或密码错误");
      account = found.get();
    }
    // 登录成功后清除旧身份，防止旧会话继续作为认证后门。
    if (jwt.token(request).isPresent()) {
      try { jwt.revokeActive(request); }
      catch (ApiException e) { if (e.status() != 401) throw e; }
    }
    HttpSession session = request.getSession(true);
    request.changeSessionId();
    session.removeAttribute("userId");
    session.removeAttribute("csrf");
    jwt.issue(account.id(), response);
    return view(account);
  }

  public ObjectNode current(HttpServletRequest request) {
    return view(accounts.findById(sessions.user(request))
        .orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "账号不存在，请重新登录")));
  }

  private String field(JsonNode body, String name) {
    JsonNode value = body.path(name);
    require(value.isTextual(), "请填写完整的账号信息");
    return value.asText();
  }

  private ObjectNode view(UserAccountRepository.Account account) {
    return obj("id", account.id(), "loginName", account.loginName(), "displayName", account.displayName());
  }
}
