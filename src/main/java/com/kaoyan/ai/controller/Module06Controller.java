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
public class Module06Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module06Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op043：创建日练周测专项训练 */
  @PostMapping("/api/v1/practice/sessions")
  public ResponseEntity<?> op043(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op043", paths, body, request);
  }

  /** op044：获取本人练习试题 */
  @GetMapping("/api/v1/practice/sessions/{id}")
  public ResponseEntity<?> op044(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op044", paths, body, request);
  }

  /** op045：提交单题作答与批改 */
  @PostMapping("/api/v1/practice/sessions/{id}/attempts")
  public ResponseEntity<?> op045(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op045", paths, body, request);
  }

  /** op046：分页查询本人错题 */
  @GetMapping("/api/v1/practice/wrong-items")
  public ResponseEntity<?> op046(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op046", paths, body, request);
  }

  /** op047：分页查询错题作答历史 */
  @GetMapping("/api/v1/practice/wrong-items/{id}/history")
  public ResponseEntity<?> op047(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op047", paths, body, request);
  }

  /** op048：获取同类专项试题 */
  @GetMapping("/api/v1/practice/similar-questions")
  public ResponseEntity<?> op048(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op048", paths, body, request);
  }

  /** op049：知识点掌握进度 */
  @GetMapping("/api/v1/practice/knowledge-progress")
  public ResponseEntity<?> op049(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op049", paths, body, request);
  }
}
