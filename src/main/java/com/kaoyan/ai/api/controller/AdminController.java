package com.kaoyan.ai.api.controller;

import static com.kaoyan.ai.common.Jsons.obj;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.service.auth.AdminAccounts;
import com.kaoyan.ai.service.auth.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final AdminAccounts admin;
  private final SessionService sessions;

  AdminController(AdminAccounts admin, SessionService sessions) {
    this.admin = admin;
    this.sessions = sessions;
  }

  @GetMapping("/users")
  public Object users(@RequestParam(defaultValue="") String email, @RequestParam(defaultValue="") String role,
      @RequestParam(defaultValue="0") int page, HttpServletRequest request) {
    return wrap(admin.users(email,role,page),request);
  }
  @GetMapping("/users/legacy")
  public Object legacy(HttpServletRequest request) { return wrap(admin.legacy(),request); }
  @GetMapping("/users/{id}")
  public Object user(@PathVariable String id,HttpServletRequest request) { return wrap(admin.user(id),request); }
  @PatchMapping("/users/{id}")
  public Object rename(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest request) {
    admin.renameUser(id,body,action(request)); return wrap(obj("updated",true),request);
  }
  @PutMapping("/users/{id}/roles")
  public Object assign(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest request) {
    admin.assign(id,body,action(request)); return wrap(obj("updated",true),request);
  }
  @PostMapping("/users/{id}/disable")
  public Object disable(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest request) {
    admin.disable(id,body,action(request)); return wrap(obj("updated",true),request);
  }
  @PostMapping("/users/{id}/enable")
  public Object enable(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest request) {
    admin.enable(id,body,action(request)); return wrap(obj("updated",true),request);
  }
  @PostMapping("/teachers")
  public ResponseEntity<?> create(@RequestBody JsonNode body,HttpServletRequest request) {
    return delivery(admin.createTeacher(body,action(request)),request,201);
  }
  @PostMapping("/users/{id}/legacy-binding")
  public ResponseEntity<?> bind(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest request) {
    return delivery(admin.bindLegacy(id,body,action(request)),request,202);
  }
  @PostMapping("/users/{id}/invitation/resend")
  public ResponseEntity<?> resend(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest request) {
    return delivery(admin.resend(id,body,action(request)),request,202);
  }
  @PostMapping("/users/{id}/invitation/cancel")
  public Object cancel(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest request) {
    admin.cancel(id,body,action(request)); return wrap(obj("updated",true),request);
  }
  @GetMapping("/roles")
  public Object roles(HttpServletRequest request) { return wrap(admin.roles(),request); }
  @GetMapping("/permissions")
  public Object permissions(HttpServletRequest request) { return wrap(admin.permissions(),request); }
  @PatchMapping("/roles/{role}")
  public Object renameRole(@PathVariable String role,@RequestBody JsonNode body,HttpServletRequest request) {
    admin.renameRole(role,body,action(request)); return wrap(obj("updated",true),request);
  }
  @PutMapping("/roles/{role}/permissions")
  public Object assignPermissions(@PathVariable String role,@RequestBody JsonNode body,HttpServletRequest request) {
    admin.assignPermissions(role,body,action(request)); return wrap(obj("updated",true),request);
  }
  @GetMapping("/audit")
  public Object logs(@RequestParam(defaultValue="0") int page,HttpServletRequest request) { return wrap(admin.logs(page),request); }
  @GetMapping("/settings")
  public Object settings(HttpServletRequest request) { return wrap(admin.settings(),request); }
  @PutMapping("/settings")
  public Object updateSettings(@RequestBody JsonNode body,HttpServletRequest request) {
    admin.settings(body,action(request)); return wrap(obj("updated",true),request);
  }

  private Object wrap(Object data,HttpServletRequest request) {
    return obj("code","OK","message","操作成功","data",data,"traceId",request.getAttribute("traceId"));
  }

  private ResponseEntity<?> delivery(Object result,HttpServletRequest request,int successStatus) {
    if (result instanceof JsonNode node && "FAILED".equals(node.path("deliveryStatus").asText()))
      return ResponseEntity.status(503).body(obj("code","MAIL_UNAVAILABLE","message","邀请未发送，可在用户列表重新发送",
          "data",result,"traceId",request.getAttribute("traceId")));
    return ResponseEntity.status(successStatus).body(wrap(result,request));
  }

  private AdminAccounts.Action action(HttpServletRequest request) {
    return new AdminAccounts.Action(sessions.user(request),String.valueOf(request.getAttribute("traceId")),request.getRemoteAddr());
  }
}
