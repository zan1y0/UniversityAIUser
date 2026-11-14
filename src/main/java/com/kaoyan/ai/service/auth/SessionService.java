package com.kaoyan.ai.service.auth;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.repository.UserAccountRepository;
import jakarta.servlet.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
  private final UserAccountRepository accounts;
  private final JwtService jwt;
  private final PermissionService permissions;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  public SessionService(UserAccountRepository accounts, JwtService jwt, PermissionService permissions) {
    this.accounts = accounts;
    this.jwt = jwt; this.permissions=permissions;
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
    var identity = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (identity != null && identity.getPrincipal() instanceof com.kaoyan.ai.api.security.RequestIdentity principal) return principal.userId();
    var token = jwt.token(r);
    if (token.isPresent()) return jwt.verify(token.get()).getSubject();
    HttpSession s = r.getSession(false);
    if (s == null || s.getAttribute("userId") == null)
      throw new ApiException(401, "UNAUTHENTICATED", "请先登录");
    return (String) s.getAttribute("userId");
  }

  public String role(HttpServletRequest r) {
    var identity = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (identity != null && identity.getPrincipal() instanceof com.kaoyan.ai.api.security.RequestIdentity principal) return principal.role();
    throw new ApiException(401,"UNAUTHENTICATED","请重新登录");
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
    permissions.requireRole(account.get().id(),"STUDENT");
    r.changeSessionId();
    session.removeAttribute("csrf");
    session.setAttribute("loginFailures", 0);
    String uid = account.get().id();
    session.setAttribute("userId", uid);
    session.setAttribute("activeRole", "STUDENT");
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
