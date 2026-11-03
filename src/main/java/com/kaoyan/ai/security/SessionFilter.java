package com.kaoyan.ai.security;

import static com.kaoyan.ai.common.Jsons.*;

import com.kaoyan.ai.common.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionFilter extends OncePerRequestFilter {
  private final Set<String> origins;
  private final SessionService sessions;

  public SessionFilter(SessionService sessions,
      @Value("${app.cors.allowed-origins:http://127.0.0.1:5173,http://localhost:5173}") String allowedOrigins) {
    this.sessions = sessions;
    this.origins = new HashSet<>(Arrays.asList(allowedOrigins.split(",")));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse w, FilterChain chain)
      throws ServletException, IOException {
    r.setAttribute("traceId", id());
    w.setHeader("X-Content-Type-Options", "nosniff");
    w.setHeader("Cache-Control", "no-store");
    String origin = r.getHeader("Origin");
    if (origin != null && origins.contains(origin)) {
      w.setHeader("Access-Control-Allow-Origin", origin);
      w.setHeader("Vary", "Origin");
      w.setHeader("Access-Control-Allow-Credentials", "true");
      w.setHeader(
          "Access-Control-Allow-Headers",
          "Content-Type,Authorization,X-CSRF-Token,Idempotency-Key,Last-Event-ID");
      w.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
    }
    if (r.getMethod().equals("OPTIONS")) {
      w.setStatus(204);
      return;
    }
    try {
      if (r.getRequestURI().startsWith("/api/v1/")) {
        HttpSession s = r.getSession(false);
        boolean publicRoute =
            Set.of("/api/v1/session/csrf", "/api/v1/session/login",
                "/api/v1/auth/login", "/api/v1/auth/register").contains(r.getRequestURI());
        if (!publicRoute) sessions.user(r);
        if (!Set.of("GET", "HEAD").contains(r.getMethod())) {
          String expected = s == null ? null : (String) s.getAttribute("csrf"),
              actual = r.getHeader("X-CSRF-Token");
          if (expected == null
              || actual == null
              || !MessageDigest.isEqual(
                  expected.getBytes(StandardCharsets.UTF_8),
                  actual.getBytes(StandardCharsets.UTF_8))
              || (origin != null
                  && !origins.contains(origin)
                  && !origin.equals(r.getScheme() + "://" + r.getServerName()
                      + ((r.getServerPort() == 80 && r.getScheme().equals("http"))
                          || (r.getServerPort() == 443 && r.getScheme().equals("https"))
                          ? "" : ":" + r.getServerPort()))))
            throw new ApiException(403, "CSRF_REJECTED", "CSRF 校验失败，请重新获取令牌");
        }
      }
      chain.doFilter(r, w);
    } catch (ApiException e) {
      w.setStatus(e.status());
      if (e.status() == 429) w.setHeader("Retry-After", "60");
      w.setContentType("application/json;charset=UTF-8");
      w.getWriter()
          .write(
              obj(
                      "code",
                      e.code(),
                      "message",
                      e.getMessage(),
                      "data",
                      null,
                      "traceId",
                      r.getAttribute("traceId"),
                      "details",
                      arr())
                  .toString());
    }
  }
}
