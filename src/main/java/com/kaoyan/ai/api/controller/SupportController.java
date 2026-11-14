package com.kaoyan.ai.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.api.contract.ApiExecutor;
import com.kaoyan.ai.service.task.EventService;
import com.kaoyan.ai.infrastructure.storage.FileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.kaoyan.ai.service.auth.EmailAuthService;
import com.kaoyan.ai.service.auth.JwtService;
import static com.kaoyan.ai.common.Jsons.obj;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SupportController {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;
  private final EmailAuthService auth;
  private final JwtService jwt;

  public SupportController(ApiExecutor api, FileService files, EventService events, EmailAuthService auth, JwtService jwt) {
    this.api = api;
    this.files = files;
    this.events = events;
    this.auth = auth;
    this.jwt = jwt;
  }

  /** op001：获取 CSRF 令牌 */
  @GetMapping("/api/v1/session/csrf")
  public ResponseEntity<?> op001(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op001", paths, body, request);
  }

  /** op002：登录 */
  @PostMapping("/api/v1/session/login")
  public ResponseEntity<?> op002(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request, HttpServletResponse response) {
    return ResponseEntity.ok(obj("code", "OK", "message", "操作成功",
        "data", auth.login(body, request, response, true), "traceId", request.getAttribute("traceId")));
  }

  /** op003：退出登录 */
  @PostMapping("/api/v1/session/logout")
  public ResponseEntity<?> op003(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request, HttpServletResponse response) {
    jwt.revoke(request, response);
    return api.execute("op003", paths, body, request);
  }

  /** op004：获取本人备考档案 */
  @GetMapping("/api/v1/profile")
  public ResponseEntity<?> op004(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op004", paths, body, request);
  }

  /** op005：替换本人备考档案 */
  @PutMapping("/api/v1/profile")
  public ResponseEntity<?> op005(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op005", paths, body, request);
  }

  /** op006：上传私有图片或音频 */
  @PostMapping("/api/v1/files")
  public ResponseEntity<?> op006(
      @RequestParam MultipartFile file, @RequestParam String purpose, HttpServletRequest request) {
    return files.upload(file, purpose, request);
  }

  @PostMapping("/api/v1/avatar/attachments")
  public ResponseEntity<?> uploadChatAttachment(@RequestParam MultipartFile file, HttpServletRequest request)
      throws java.io.IOException {
    return files.uploadChatAttachment(file, request);
  }

  /** op007：下载本人私有附件 */
  @GetMapping("/api/v1/files/{id}")
  public ResponseEntity<?> op007(@PathVariable String id, HttpServletRequest request) {
    return files.download(id, request);
  }

  /** op008：删除本人附件 */
  @DeleteMapping("/api/v1/files/{id}")
  public ResponseEntity<?> op008(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op008", paths, body, request);
  }

  /** op009：查询本人异步任务及类型化结果 */
  @GetMapping("/api/v1/jobs/{id}")
  public ResponseEntity<?> op009(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op009", paths, body, request);
  }

  /** op010：订阅本人 SSE 事件 */
  @GetMapping("/api/v1/events")
  public org.springframework.web.servlet.mvc.method.annotation.SseEmitter op010(
      HttpServletRequest request) {
    return events.subscribe(request);
  }
}
