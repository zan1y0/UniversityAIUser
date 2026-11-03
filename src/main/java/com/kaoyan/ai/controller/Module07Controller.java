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
public class Module07Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module07Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op050：生成候选学习计划 */
  @PostMapping("/api/v1/plans")
  public ResponseEntity<?> op050(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op050", paths, body, request);
  }

  /** op051：查询日周月学习计划 */
  @GetMapping("/api/v1/plans/{id}")
  public ResponseEntity<?> op051(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op051", paths, body, request);
  }

  /** op052：调整候选计划任务 */
  @PatchMapping("/api/v1/plans/{id}/tasks/{taskId}")
  public ResponseEntity<?> op052(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op052", paths, body, request);
  }

  /** op053：确认候选计划 */
  @PostMapping("/api/v1/plans/{id}/confirm")
  public ResponseEntity<?> op053(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op053", paths, body, request);
  }

  /** op054：根据进度调整计划 */
  @PostMapping("/api/v1/plans/{id}/adjustments")
  public ResponseEntity<?> op054(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op054", paths, body, request);
  }

  /** op055：标记计划任务完成 */
  @PostMapping("/api/v1/plans/{id}/tasks/{taskId}/complete")
  public ResponseEntity<?> op055(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op055", paths, body, request);
  }

  /** op056：查询计划完成进度 */
  @GetMapping("/api/v1/plans/{id}/progress")
  public ResponseEntity<?> op056(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op056", paths, body, request);
  }
}
