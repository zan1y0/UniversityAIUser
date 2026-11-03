package com.kaoyan.ai.controller;

import static com.kaoyan.ai.common.Jsons.obj;
import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.security.*;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;
  private final JwtService jwt;
  private final SessionService sessions;
  public AuthController(AuthService auth, JwtService jwt, SessionService sessions) {
    this.auth = auth; this.jwt = jwt; this.sessions = sessions;
  }
  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response) {
    return result(201, auth.authenticate(body, true, request, response), request);
  }
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response) {
    return result(200, auth.authenticate(body, false, request, response), request);
  }
  @GetMapping("/me")
  public ResponseEntity<?> me(HttpServletRequest request) { return result(200, auth.current(request), request); }
  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
    jwt.revoke(request, response);
    sessions.logout(request);
    return ResponseEntity.noContent().build();
  }
  private ResponseEntity<?> result(int status, JsonNode data, HttpServletRequest request) {
    return ResponseEntity.status(status).body(obj("code", "OK", "message", "操作成功", "data", data, "traceId", request.getAttribute("traceId")));
  }
}
