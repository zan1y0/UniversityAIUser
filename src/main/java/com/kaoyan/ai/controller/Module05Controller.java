package com.kaoyan.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.contract.ApiExecutor;
import com.kaoyan.ai.jobs.EventService;
import com.kaoyan.ai.storage.FileService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class Module05Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module05Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op037：创建多轮答疑会话 */
  @PostMapping("/api/v1/qa/conversations")
  public ResponseEntity<?> op037(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op037", paths, body, request);
  }

  /** op038：识别题目图片 */
  @PostMapping("/api/v1/qa/image-recognitions")
  public ResponseEntity<?> op038(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op038", paths, body, request);
  }

  /** op039：发送文字题或已确认图片题 */
  @PostMapping("/api/v1/qa/conversations/{id}/messages")
  public ResponseEntity<?> op039(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op039", paths, body, request);
  }

  /** op040：分页获取会话消息 */
  @GetMapping("/api/v1/qa/conversations/{id}/messages")
  public ResponseEntity<?> op040(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op040", paths, body, request);
  }

  /** op041：获取最终答疑消息 */
  @GetMapping("/api/v1/qa/messages/{id}")
  public ResponseEntity<?> op041(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op041", paths, body, request);
  }

  /** op042：取消生成 */
  @PostMapping("/api/v1/qa/messages/{id}/cancel")
  public ResponseEntity<?> op042(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op042", paths, body, request);
  }
}
