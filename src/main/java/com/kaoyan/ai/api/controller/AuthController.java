package com.kaoyan.ai.api.controller;

import static com.kaoyan.ai.common.Jsons.obj;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.service.auth.*;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final EmailAuthService auth;
  private final AccountLifecycle lifecycle;
  private final JwtService jwt;
  private final SessionService sessions;
  public AuthController(EmailAuthService auth, AccountLifecycle lifecycle, JwtService jwt, SessionService sessions) {
    this.auth = auth; this.lifecycle = lifecycle; this.jwt = jwt; this.sessions = sessions;
  }
  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response) {
    return result(201, auth.register(body, request, response), request);
  }
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response) {
    return result(200, auth.login(body, request, response, false), request);
  }
  @PostMapping("/workspace")
  public ResponseEntity<?> workspace(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response) {
    return result(200, auth.switchRole(body, request, response), request);
  }
  @PostMapping("/email/code")
  public ResponseEntity<?> code(@RequestBody JsonNode body, HttpServletRequest request) {
    lifecycle.requestCode(body, request.getRemoteAddr());
    return result(202, obj("accepted", true), request);
  }
  @PostMapping("/password/reset")
  public ResponseEntity<?> reset(@RequestBody JsonNode body) {
    lifecycle.reset(body);
    return ResponseEntity.noContent().build();
  }
  @PostMapping("/invitation/check")
  public ResponseEntity<?> invitation(@RequestBody JsonNode body, HttpServletRequest request) {
    AccountInputs.fields(body, "token");
    return result(200, lifecycle.invitation(AccountInputs.string(body,"token")), request);
  }
  @PostMapping("/invitation/activate")
  public ResponseEntity<?> activate(@RequestBody JsonNode body) {
    lifecycle.activate(body);
    return ResponseEntity.noContent().build();
  }
  @PostMapping("/email/change-code")
  public ResponseEntity<?> changeCode(@RequestBody JsonNode body, HttpServletRequest request) {
    lifecycle.sendChange(sessions.user(request), body, request.getRemoteAddr());
    return result(202, obj("accepted", true), request);
  }
  @PostMapping("/email/change")
  public ResponseEntity<?> change(@RequestBody JsonNode body, HttpServletRequest request) {
    lifecycle.changeEmail(sessions.user(request), body, String.valueOf(request.getAttribute("traceId")));
    return ResponseEntity.noContent().build();
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
