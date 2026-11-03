package com.kaoyan.ai.security;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.UserAccountRepository;
import jakarta.servlet.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
  private final UserAccountRepository accounts;
  private final JwtService jwt;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  public SessionService(UserAccountRepository accounts, JwtService jwt) {
    this.accounts = accounts;
    this.jwt = jwt;
  }

  public String csrf(HttpServletRequest r) {
    HttpSession s = r.getSession(true);
    String token = (String) s.getAttribute("csrf");
    if (token == null) {
      token = id() + id();
      s.setAttribute("csrf", token);
    }
    return token;
  }

  public String user(HttpServletRequest r) {
    var token = jwt.token(r);
    if (token.isPresent()) return jwt.verify(token.get()).getSubject();
    HttpSession s = r.getSession(false);
    if (s == null || s.getAttribute("userId") == null)
      throw new ApiException(401, "UNAUTHENTICATED", "请先登录");
    return (String) s.getAttribute("userId");
  }

  public ObjectNode login(HttpServletRequest r, String name, String password) {
    HttpSession session = r.getSession(true);
    long now = System.currentTimeMillis();
    Object until = session.getAttribute("loginWindowUntil");
    if (until == null || (long) until < now) {
      session.setAttribute("loginWindowUntil", now + 60000);
      session.setAttribute("loginFailures", 0);
    }
    int attempts =
        session.getAttribute("loginFailures") == null
            ? 0
            : (int) session.getAttribute("loginFailures");
    if (attempts >= 10) throw new ApiException(429, "RATE_LIMITED", "登录尝试过多，请稍后重试");
    var account = accounts.findByLoginName(name);
    if (account.isEmpty() || !encoder.matches(password, account.get().passwordHash())) {
      session.setAttribute("loginFailures", attempts + 1);
      throw new ApiException(401, "INVALID_CREDENTIALS", "账号或密码错误");
    }
    r.changeSessionId();
    session.removeAttribute("csrf");
    session.setAttribute("loginFailures", 0);
    String uid = account.get().id();
    session.setAttribute("userId", uid);
    return obj(
        "id",
        uid,
        "loginName",
        account.get().loginName(),
        "displayName",
        account.get().displayName());
  }

  public void logout(HttpServletRequest r) {
    HttpSession s = r.getSession(false);
    if (s != null) s.invalidate();
  }
}
